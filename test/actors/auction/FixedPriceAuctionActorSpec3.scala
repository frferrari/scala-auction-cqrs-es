package actors.auction

import java.time.Instant

import actors.ActorCommonsSpec
import actors.auction.AuctionActor._
import actors.auction.fsm.{ClosedState, FinishedAuction}
import actors.auctionSupervisor.AuctionSupervisor
import actors.user.UserActor.UserRegisteredReply
import actors.userSupervisor.UserSupervisor
import actors.userUnicity.UserUnicityActor
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import cqrs.UsersBid
import cqrs.commands._
import models.{AuctionReason, BidRejectionReason}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import play.api.inject.BindingKey
import play.api.inject.guice.GuiceApplicationBuilder

import scala.concurrent.duration._

/**
  * Created by Francois FERRARI on 17/05/2017
  */
class FixedPriceAuctionActorSpec3() extends TestKit(ActorSystem("AuctionSystem"))
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

  val auction = makeFixedPriceAuction(
    startPrice = 0.10,
    startsAt = Instant.now().plusSeconds(2),
    lastsSeconds = 20,
    stock = 10,
    hasAutomaticRenewal = false,
    sellerId = seller.userId
  )

  "A scheduled FIXED PRICE auction" should {

    "successfully create a User (Seller)" in {
      userSupervisorActorRef ! CreateUser(seller)
      expectMsg(UserRegisteredReply)
    }

    "successfully create a User (bidderA)" in {
      userSupervisorActorRef ! CreateUser(bidderA)
      expectMsg(UserRegisteredReply)
    }

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
}
