package actors.auction

import java.time.Instant

import actors.ActorCommonsSpec
import actors.auction.AuctionActor._
import actors.auction.fsm.{ActiveAuction, ClosedState, FinishedAuction, StartedState}
import actors.auctionSupervisor.AuctionSupervisor
import actors.user.UserActor.UserRegisteredReply
import actors.userSupervisor.UserSupervisor
import actors.userUnicity.UserUnicityActor
import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.testkit.{ImplicitSender, TestKit}
import cqrs.UsersBid
import cqrs.commands.{CreateAuction, CreateUser, GetCurrentState, PlaceBid}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import play.api.inject.BindingKey
import play.api.inject.guice.GuiceApplicationBuilder

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Created by Francois FERRARI on 21/05/2017
  */
class AuctionActorSpec() extends TestKit(ActorSystem("AuctionSystem"))
  with ActorCommonsSpec
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with AuctionActorHelpers {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
    Await.ready(system.terminate(), 5.seconds)
  }

  val app = new GuiceApplicationBuilder().build()
  val injector = app.injector
  val userUnicityActorRef = injector.instanceOf(BindingKey(classOf[ActorRef]).qualifiedWith(UserUnicityActor.name))
  val userSupervisorActorRef = system.actorOf(Props(new UserSupervisor(userUnicityActorRef)), name = UserSupervisor.name)
  val auctionSupervisorActorRef = system.actorOf(Props(new AuctionSupervisor()), name = AuctionSupervisor.name)

  val seller = makeUser("contact@pluto.space", "hhgg", "Robert", "John")
  val bidderA = makeUser("bidderA@pluto.space", "bidderA", "BidderA", "John")
  val bidderB = makeUser("bidderB@pluto.space", "bidderB", "BidderB", "John")

  "An AUCTION should" should {
    "successfully create a User (Seller)" in {
      userSupervisorActorRef ! CreateUser(seller)
      expectMsg(UserRegisteredReply)
    }

    "successfully create a User (bidderA)" in {
      userSupervisorActorRef ! CreateUser(bidderA)
      expectMsg(UserRegisteredReply)
    }

    "successfully create a User (bidderB)" in {
      userSupervisorActorRef ! CreateUser(bidderB)
      expectMsg(UserRegisteredReply)
    }
  }

  "An AUCTION W/O reserve price W/O automatic renewal W/O bidders" should {

    lazy val auction = makeAuction(
      startPrice = 0.10,
      bidIncrement = 0.10,
      startsAt = Instant.now(),
      lastsSeconds = 4,
      hasAutomaticRenewal = false,
      hasTimeExtension = false,
      sellerId = seller.userId
    )

    "successfully create an auction" in {
      auctionSupervisorActorRef ! CreateAuction(auction)
      expectMsg(AuctionStartedReply)
    }

    "be in CLOSED state when it has reached it's end time" in {
      expectNoMsg(secondsToWaitForAuctionEnd(auction).seconds)

      getAuctionActorSelection(auction.auctionId) ! GetCurrentState
      expectMsgPF() {
        case CurrentStateReply(ClosedState, FinishedAuction(finishedAuction))
          if finishedAuction.bids.isEmpty &&
            finishedAuction.currentPrice == finishedAuction.startPrice &&
            finishedAuction.closedBy.isDefined &&
            finishedAuction.closedAt.isDefined &&
            ! finishedAuction.isSold
        => ()
      }

      getAuctionActorSelection(auction.auctionId) ! PoisonPill
      expectNoMsg(2.seconds)
    }
  }

  "An AUCTION W/O reserve price W/automatic renewal W/O bidders" should {

    lazy val auction = makeAuction(
      startPrice = 0.10,
      bidIncrement = 0.10,
      startsAt = Instant.now().plusSeconds(4),
      lastsSeconds = 10,
      hasAutomaticRenewal = true,
      hasTimeExtension = false,
      sellerId = seller.userId
    )

    "successfully create an auction in Scheduled state" in {
      auctionSupervisorActorRef ! CreateAuction(auction)
      expectMsg(AuctionScheduledReply)
    }

    "successfully check that the auction is now in StartedState" in {
      expectNoMsg(secondsToWaitForAuctionStart(auction).seconds)
      getAuctionActorSelection(auction.auctionId) ! GetCurrentState
      expectMsgPF() {
        case (CurrentStateReply(StartedState, _: ActiveAuction)) => ()
      }
    }

    "be RESTARTED when it has reached it's end time" in {
      expectNoMsg(secondsToWaitForAuctionEnd(auction).seconds)

      getAuctionActorSelection(auction.auctionId) ! GetCurrentState
      expectMsgPF() {
        case CurrentStateReply(StartedState, ActiveAuction(restartedAuction))
          if restartedAuction.bids.isEmpty &&
            restartedAuction.currentPrice == restartedAuction.startPrice &&
            restartedAuction.closedBy.isEmpty &&
            restartedAuction.closedAt.isEmpty &&
            restartedAuction.startsAt == auction.endsAt &&
            restartedAuction.endsAt.isAfter(restartedAuction.startsAt) &&
            ! restartedAuction.isSold
        => ()
      }
    }
  }

  "An AUCTION W/O reserve price W/1 bidder" should {

    lazy val auction = makeAuction(
      startPrice = 0.10,
      bidIncrement = 0.10,
      startsAt = Instant.now(),
      lastsSeconds = 20,
      hasAutomaticRenewal = false,
      hasTimeExtension = false,
      seller.userId
    )

    "successfully create an auction in StartedState" in {
      auctionSupervisorActorRef ! CreateAuction(auction)
      expectMsg(AuctionStartedReply)
    }

    "accept a bid from bidderA" in {
      getAuctionActorSelection(auction.auctionId) ! PlaceBid(UsersBid(auction.auctionId, bidderA.nickName, bidderA.userId, 1, 4.00, Instant.now()))
      expectMsg(BidPlacedReply)

      getAuctionActorSelection(auction.auctionId) ! GetCurrentState
      val expectedBidEssentials = List(
        (bidderA.userId, 1, auction.startPrice, 4.0, true, false, false)
      )
      expectMsgPF() {
        case CurrentStateReply(StartedState, ActiveAuction(activeAuction))
          if activeAuction.currentPrice == activeAuction.startPrice &&
            activeAuction.bids.length == 1 &&
            activeAuction.bids.head.bidPrice == activeAuction.startPrice &&
            activeAuction.bids.head.bidMaxPrice == 4.0 &&
            activeAuction.bids.head.bidderId == bidderA.userId &&
            bidEssentials(activeAuction.bids) == expectedBidEssentials
        => ()
      }
    }

    "be in CLOSED state when it has reached it's end time with bidderA as a winner" in {
      expectNoMsg(secondsToWaitForAuctionEnd(auction).seconds)

      getAuctionActorSelection(auction.auctionId) ! GetCurrentState
      expectMsgPF() {
        case CurrentStateReply(ClosedState, FinishedAuction(finishedAuction))
          if finishedAuction.bids.length == 1 &&
            finishedAuction.currentPrice == finishedAuction.startPrice &&
            finishedAuction.closedBy.isDefined &&
            finishedAuction.closedAt.isDefined &&
            finishedAuction.isSold
        => ()
      }
    }
  }

  "An AUCTION W/reserve price W/1 bidder lower than the reserve price" should {

    lazy val auction = makeAuction(
      startPrice = 0.10,
      bidIncrement = 0.10,
      startsAt = Instant.now(),
      lastsSeconds = 6,
      hasAutomaticRenewal = false,
      hasTimeExtension = false,
      sellerId = seller.userId,
      reservePrice = Some(8)
    )

    "successfully create an auction in StartedState" in {
      auctionSupervisorActorRef ! CreateAuction(auction)
      expectMsg(AuctionStartedReply)
    }

    "accept a bid from bidderA" in {
      getAuctionActorSelection(auction.auctionId) ! PlaceBid(UsersBid(auction.auctionId, bidderA.nickName, bidderA.userId, 1, 4.00, Instant.now()))
      expectMsg(BidPlacedReply)

      getAuctionActorSelection(auction.auctionId) ! GetCurrentState
      val expectedBidEssentials = List(
        (bidderA.userId, 1, auction.startPrice, 4.0, true, false, false)
      )
      expectMsgPF() {
        case CurrentStateReply(StartedState, ActiveAuction(activeAuction))
          if activeAuction.currentPrice == activeAuction.startPrice &&
            activeAuction.bids.length == 1 &&
            activeAuction.bids.head.bidPrice == activeAuction.startPrice &&
            activeAuction.bids.head.bidMaxPrice == 4.0 &&
            activeAuction.bids.head.bidderId == bidderA.userId &&
            bidEssentials(activeAuction.bids) == expectedBidEssentials
        => ()
      }
    }

    "be in CLOSED state when it has reached it's end time without winner" in {
      expectNoMsg(secondsToWaitForAuctionEnd(auction).seconds)

      getAuctionActorSelection(auction.auctionId) ! GetCurrentState
      expectMsgPF() {
        case CurrentStateReply(ClosedState, FinishedAuction(finishedAuction))
          if finishedAuction.bids.length == 1 &&
            finishedAuction.currentPrice == finishedAuction.startPrice &&
            finishedAuction.closedBy.isDefined &&
            finishedAuction.closedAt.isDefined &&
            !finishedAuction.isSold
        => ()
      }
    }
  }

  "An AUCTION W/reserve price W/1 bidder equal to the reserve price, he wins at the reserve price value" should {

    lazy val auction = makeAuction(
      startPrice = 0.10,
      bidIncrement = 0.10,
      startsAt = Instant.now(),
      lastsSeconds = 6,
      hasAutomaticRenewal = false,
      hasTimeExtension = false,
      sellerId = seller.userId,
      reservePrice = Some(8)
    )

    "successfully create an auction in StartedState" in {
      auctionSupervisorActorRef ! CreateAuction(auction)
      expectMsg(AuctionStartedReply)
    }

    "accept a bid from bidderA" in {
      getAuctionActorSelection(auction.auctionId) ! PlaceBid(UsersBid(auction.auctionId, bidderA.nickName, bidderA.userId, 1, 8.00, Instant.now()))
      expectMsg(BidPlacedReply)

      getAuctionActorSelection(auction.auctionId) ! GetCurrentState
      val expectedBidEssentials = List(
        (bidderA.userId, 1, 8.0, 8.0, true, false, false)
      )
      expectMsgPF() {
        case CurrentStateReply(StartedState, ActiveAuction(activeAuction))
          if activeAuction.currentPrice == 8.0 &&
            activeAuction.bids.length == 1 &&
            activeAuction.bids.head.bidPrice == 8.0 &&
            activeAuction.bids.head.bidMaxPrice == 8.0 &&
            activeAuction.bids.head.bidderId == bidderA.userId &&
            bidEssentials(activeAuction.bids) == expectedBidEssentials
        => ()
      }
    }

    "be in CLOSED state when it has reached it's end time with a winner" in {
      expectNoMsg(secondsToWaitForAuctionEnd(auction).seconds)

      getAuctionActorSelection(auction.auctionId) ! GetCurrentState
      expectMsgPF() {
        case CurrentStateReply(ClosedState, FinishedAuction(finishedAuction))
          if finishedAuction.bids.length == 1 &&
            finishedAuction.currentPrice == 8.0 &&
            finishedAuction.closedBy.isDefined &&
            finishedAuction.closedAt.isDefined &&
            finishedAuction.isSold
        => ()
      }
    }
  }

  "An AUCTION W/reserve price W/1 bidder greater than the reserve price, he wins at the reserve price value" should {

    lazy val auction = makeAuction(
      startPrice = 0.10,
      bidIncrement = 0.10,
      startsAt = Instant.now(),
      lastsSeconds = 6,
      hasAutomaticRenewal = false,
      hasTimeExtension = false,
      sellerId = seller.userId,
      reservePrice = Some(8)
    )

    "successfully create an auction in StartedState" in {
      auctionSupervisorActorRef ! CreateAuction(auction)
      expectMsg(AuctionStartedReply)
    }

    "accept a bid from bidderA" in {
      getAuctionActorSelection(auction.auctionId) ! PlaceBid(UsersBid(auction.auctionId, bidderA.nickName, bidderA.userId, 1, 9.00, Instant.now()))
      expectMsg(BidPlacedReply)

      getAuctionActorSelection(auction.auctionId) ! GetCurrentState
      val expectedBidEssentials = List(
        (bidderA.userId, 1, 8.0, 9.0, true, false, false)
      )
      expectMsgPF() {
        case CurrentStateReply(StartedState, ActiveAuction(activeAuction))
          if activeAuction.currentPrice == 8.0 &&
            bidEssentials(activeAuction.bids) == expectedBidEssentials
        => ()
      }
    }

    "be in CLOSED state when it has reached it's end time with winner" in {
      expectNoMsg(secondsToWaitForAuctionEnd(auction).seconds)

      getAuctionActorSelection(auction.auctionId) ! GetCurrentState
      expectMsgPF() {
        case CurrentStateReply(ClosedState, FinishedAuction(finishedAuction))
          if finishedAuction.bids.length == 1 &&
            finishedAuction.currentPrice == 8.0 &&
            finishedAuction.closedBy.isDefined &&
            finishedAuction.closedAt.isDefined &&
            finishedAuction.isSold
        => ()
      }
    }
  }

}
