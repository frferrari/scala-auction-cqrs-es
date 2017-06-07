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
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import cqrs.UsersBid
import cqrs.commands.{CreateAuction, CreateUser, GetCurrentState, PlaceBid}
import models.{AuctionReason, BidRejectionReason}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import play.api.inject.BindingKey
import play.api.inject.guice.GuiceApplicationBuilder

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Created by Francois FERRARI on 17/05/2017
  */
class FixedPriceAuctionActorSpec() extends TestKit(ActorSystem("AuctionSystem"))
  with ActorCommonsSpec
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with AuctionActorHelpers {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  val app = new GuiceApplicationBuilder().build()
  val injector = app.injector
  val userUnicityActorRef = injector.instanceOf(BindingKey(classOf[ActorRef]).qualifiedWith(UserUnicityActor.name))
  val userSupervisorActorRef = system.actorOf(Props(new UserSupervisor(userUnicityActorRef)), name = UserSupervisor.name)
  val auctionSupervisorActorRef = system.actorOf(Props(new AuctionSupervisor()), name = AuctionSupervisor.name)

  val seller = makeUser("contact@pluto.space", "hhgg", "Robert", "John")
  val bidderA = makeUser("bidderA@pluto.space", "bidderA", "BidderA", "John")

  "A FIXE PRICE auction" should {
    "successfully create a User (Seller)" in {
      userSupervisorActorRef ! CreateUser(seller)
      expectMsg(UserRegisteredReply)
    }

    "successfully create a User (bidderA)" in {
      userSupervisorActorRef ! CreateUser(bidderA)
      expectMsg(UserRegisteredReply)
    }
  }

  "A scheduled FIXED PRICE auction W/O bids W/O automatic renewal" should {

    lazy val auction = makeFixedPriceAuction(
      startPrice = 0.10,
      startsAt = Instant.now().plusSeconds(2),
      lastsSeconds = 5,
      stock = 10,
      hasAutomaticRenewal = false,
      sellerId = seller.userId
    )

    "successfully create an auction in StartedState" in {
      auctionSupervisorActorRef ! CreateAuction(auction)
      expectMsg(AuctionScheduledReply)
    }

    "be in CLOSED state and NOT SOLD when endsAt is reached" in {
      expectNoMsg(secondsToWaitForAuctionEnd(auction).seconds)

      getAuctionActorSelection(auction.auctionId) ! GetCurrentState
      expectMsgPF() {
        case CurrentStateReply(ClosedState, FinishedAuction(finishedAuction))
          if finishedAuction.bids.isEmpty &&
            !finishedAuction.isSold
        => ()
      }
    }
  }

  "A scheduled FIXED PRICE auction W/O bids W/automatic renewal" should {

    lazy val auction = makeFixedPriceAuction(
      startPrice = 0.10,
      startsAt = Instant.now().plusSeconds(2),
      lastsSeconds = 5,
      stock= 10,
      hasAutomaticRenewal = true,
      sellerId = seller.userId
    )

    "successfully create an auction in StartedState" in {
      auctionSupervisorActorRef ! CreateAuction(auction)
      expectMsg(AuctionScheduledReply)
    }

    "be RESTARTED and NOT SOLD when endsAt is reached" in {
      expectNoMsg(secondsToWaitForAuctionEnd(auction).seconds)

      getAuctionActorSelection(auction.auctionId) ! GetCurrentState
      expectMsgPF() {
        case CurrentStateReply(StartedState, ActiveAuction(activeAuction))
          if activeAuction.bids.isEmpty &&
            activeAuction.startsAt == auction.endsAt &&
            activeAuction.endsAt.isAfter(auction.endsAt) &&
            !activeAuction.isSold
        => ()
      }
    }
  }

  "A scheduled FIXED PRICE auction W/bids" should {

    lazy val auction = makeFixedPriceAuction(
      startPrice = 0.10,
      startsAt = Instant.now().plusSeconds(2),
      lastsSeconds = 20,
      stock = 10,
      hasAutomaticRenewal = false,
      sellerId = seller.userId
    )

    "successfully create an auction in ScheduledState" in {
      auctionSupervisorActorRef ! CreateAuction(auction)
      expectMsg(AuctionScheduledReply)
    }

    "reject a bid on an auction not yet started" in {
      getAuctionActorSelection(auction.auctionId) ! PlaceBid(UsersBid(auction.auctionId, bidderA.nickName, bidderA.userId, auction.stock, auction.currentPrice, Instant.now()))
      expectMsgPF() {
        case BidRejectedReply(_, BidRejectionReason.AUCTION_NOT_YET_STARTED) => ()
      }
    }

    "reject a bid made by it's owner" in {
      expectNoMsg(secondsToWaitForAuctionStart(auction).seconds) // Let the auction start

      getAuctionActorSelection(auction.auctionId) ! PlaceBid(UsersBid(auction.auctionId, seller.nickName, seller.userId, auction.stock, auction.currentPrice, Instant.now()))
      expectMsgPF(10.seconds) {
        case BidRejectedReply(_, BidRejectionReason.SELF_BIDDING) => ()
      }
    }

    // TODO Bid on a seller's account locked
    // TODO Bid on a bidder's account locked

    "reject a bid with a quantity lower than 1" in {
      getAuctionActorSelection(auction.auctionId) ! PlaceBid(UsersBid(auction.auctionId, bidderA.nickName, bidderA.userId, 0, auction.currentPrice, Instant.now()))
      expectMsgPF(10.seconds) {
        case BidRejectedReply(_, BidRejectionReason.WRONG_REQUESTED_QTY) => ()
      }
    }

    "reject a bid with a quantity higher than the available stock" in {
      getAuctionActorSelection(auction.auctionId) ! PlaceBid(UsersBid(auction.auctionId, bidderA.nickName, bidderA.userId, auction.stock + 1, auction.currentPrice, Instant.now()))
      expectMsgPF(10.seconds) {
        case BidRejectedReply(_, BidRejectionReason.NOT_ENOUGH_STOCK) => ()
      }
    }

    "reject a bid with a price different from the auction's current price" in {
      getAuctionActorSelection(auction.auctionId) ! PlaceBid(UsersBid(auction.auctionId, bidderA.nickName, bidderA.userId, auction.stock, auction.currentPrice + 1.0, Instant.now()))
      expectMsgPF(10.seconds) {
        case BidRejectedReply(_, BidRejectionReason.WRONG_BID_PRICE) => ()
      }
    }

    "accept a bid of the auction's stock value and close the auction" in {
      getAuctionActorSelection(auction.auctionId) ! PlaceBid(UsersBid(auction.auctionId, bidderA.nickName, bidderA.userId, auction.stock, auction.currentPrice, Instant.now()))
      expectMsgPF(10.seconds) {
        case AuctionClosedReply(AuctionReason.BID_NO_REMAINING_STOCK) => ()
      }
    }

    "be in CLOSED and SOLD state" in {
      getAuctionActorSelection(auction.auctionId) ! GetCurrentState
      val expectedBidEssentials = List(
        (bidderA.userId, 10, auction.currentPrice, auction.currentPrice, true, false, false)
      )
      expectMsgPF() {
        case CurrentStateReply(ClosedState, FinishedAuction(finishedAuction))
          if finishedAuction.currentPrice == auction.currentPrice &&
            bidEssentials(finishedAuction.bids) == expectedBidEssentials &&
            finishedAuction.isSold
        => ()
      }
    }

    // TODO "accept a bid with a qty lower than the auction's available stock, close the current auction and create another one with the remaining stock" in {

    "reject a bid placed on a closed auction" in {
      getAuctionActorSelection(auction.auctionId) ! PlaceBid(UsersBid(auction.auctionId, bidderA.nickName, bidderA.userId, auction.stock, auction.currentPrice, Instant.now()))
      expectMsgPF() {
        case BidRejectedReply(_, BidRejectionReason.AUCTION_HAS_ENDED) => ()
      }
    }

    // TODO ADD tests to check if a new auction has been created with the remaining stock

    //    "NOT be CLOSED after receiving a bid for a quantity lower than the available stock" in {
    //      val getAuctionActorSelection(auction.auctionId) = AuctionActor.createAuctionActor()
    //      val scheduledFixedPriceAuction = getScheduledFixedPriceAuction
    //
    //      getAuctionActorSelection(auction.auctionId) ! ScheduleAuction(scheduledFixedPriceAuction)
    //      expectNoMsg(10.seconds)
    //      getAuctionActorSelection(auction.auctionId) ! PlaceBid(UsersBid(auction.auctionId, bidderA.nickName, bidderA.userId, scheduledFixedPriceAuction.stock-1, scheduledFixedPriceAuction.currentPrice, Instant.now()))
    //      expectMsg(10.seconds, BidPlacedReply)
    //    }
  }

  "A FIXED PRICE auction W/bids" should {

    val originalStock = 10
    val requestedStock = 8

    lazy val auction = makeFixedPriceAuction(
      startPrice = 0.10,
      startsAt = Instant.now(),
      lastsSeconds = 30,
      stock = originalStock,
      hasAutomaticRenewal = false,
      sellerId = seller.userId
    )

    implicit val askTimeout = Timeout(2.seconds)

    "successfully create an auction in StartedState" in {
      auctionSupervisorActorRef ! CreateAuction(auction)
      expectMsg(AuctionStartedReply)
    }

    "accept a bid on an auction for a part of the stock" in {
      getAuctionActorSelection(auction.auctionId) ! PlaceBid(UsersBid(auction.auctionId, bidderA.nickName, bidderA.userId, requestedStock, auction.currentPrice, Instant.now()))
      expectMsg(BidPlacedReply)
    }

    "successfully check that the auction is closed and that it contains a reference to the cloned auction" in {
      expectNoMsg(2.seconds)

      getAuctionActorSelection(auction.auctionId) ! GetCurrentState
      expectMsgPF() {
        case CurrentStateReply(ClosedState, FinishedAuction(finishedAuction))
          if finishedAuction.bids.length == 1 &&
            finishedAuction.stock == 0 &&
            finishedAuction.originalStock == requestedStock &&
            finishedAuction.isSold &&
            finishedAuction.clonedToAuctionId.isDefined => ()
      }
    }

    "successfully check that the cloned auction is in StartedState and that it contains a reference to its parent auction with the remaining stock" in {
      expectNoMsg(2.seconds)

      Await.result((getAuctionActorSelection(auction.auctionId) ? GetCurrentState).mapTo[CurrentStateReply], 2.seconds) match {
        case CurrentStateReply(ClosedState, FinishedAuction(finishedAuction)) =>
          // Get the CurrentState of the cloned auction
          getAuctionActorSelection(finishedAuction.clonedToAuctionId.get) ! GetCurrentState

          expectMsgPF() {
            case CurrentStateReply(StartedState, ActiveAuction(clonedAuction))
              if clonedAuction.clonedFromAuctionId.contains(finishedAuction.auctionId) &&
                clonedAuction.clonedToAuctionId.isEmpty &&
                clonedAuction.endsAt == finishedAuction.endsAt &&
                clonedAuction.stock == (originalStock-requestedStock) &&
                clonedAuction.originalStock == (originalStock-requestedStock) => ()
          }

          // To make the cloned auction unsubscribe from the seller actor's events
          getAuctionActorSelection(finishedAuction.clonedToAuctionId.get) ! PoisonPill

        case _ =>
          fail
      }
    }

    "wait for auction actor end" in {
      // To make the original auction unsubscribe from the seller actor's events
      getAuctionActorSelection(auction.auctionId) ! PoisonPill
      expectNoMsg(2.seconds)
    }
  }

}
