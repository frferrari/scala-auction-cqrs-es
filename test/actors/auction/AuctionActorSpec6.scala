package actors.auction

import java.time.Instant

import actors.ActorCommonsSpec
import actors.auction.AuctionActor._
import actors.auction.fsm.{ActiveAuction, ClosedState, FinishedAuction, StartedState}
import actors.auctionSupervisor.AuctionSupervisor
import actors.user.UserActor.UserRegisteredReply
import actors.userSupervisor.UserSupervisor
import actors.userUnicity.UserUnicityActor
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import cqrs.UsersBid
import cqrs.commands._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import play.api.inject.BindingKey
import play.api.inject.guice.GuiceApplicationBuilder

import scala.concurrent.duration._

/**
  * Created by Francois FERRARI on 21/05/2017
  */
class AuctionActorSpec6() extends TestKit(ActorSystem("AuctionSystem"))
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

  val auction = makeAuction(
    startPrice = 0.10,
    bidIncrement = 0.10,
    startsAt = Instant.now(),
    lastsSeconds = 6,
    hasAutomaticRenewal = false,
    hasTimeExtension = false,
    sellerId = seller.userId,
    reservePrice = Some(8)
  )

  "An AUCTION W/reserve price W/1 bidder equal to the reserve price, he wins at the reserve price value" should {

    "successfully create a User (Seller)" in {
      userSupervisorActorRef ! CreateUser(seller)
      expectMsg(UserRegisteredReply)
    }

    "successfully create a User (bidderA)" in {
      userSupervisorActorRef ! CreateUser(bidderA)
      expectMsg(UserRegisteredReply)
    }

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
}
