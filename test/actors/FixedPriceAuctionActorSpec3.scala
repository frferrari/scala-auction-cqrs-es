package actors

import java.time.Instant

import actors.auction.AuctionActor
import actors.auction.AuctionActor._
import actors.auction.fsm.{ClosedState, FinishedAuction}
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import cqrs.UsersBid
import cqrs.commands.{GetCurrentState, PlaceBid, ScheduleAuction}
import models.{AuctionReason, BidRejectionReason}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

/**
  * Created by Francois FERRARI on 17/05/2017
  */
class FixedPriceAuctionActorSpec3() extends TestKit(ActorSystem("AuctionActorSpec"))
  with ActorCommonsSpec
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "A scheduled FIXED PRICE auction" should {

    val auction = makeFixedPriceAuction(0.10, Instant.now().plusSeconds(2), 20, 10, hasAutomaticRenewal = false, sellerAUUID)
    val auctionActor = AuctionActor.createAuctionActor(auction)
    auctionActor ! ScheduleAuction(auction)

    "reject a bid on an auction not yet started" in {
      expectMsg(AuctionScheduledReply)

      auctionActor ! PlaceBid(UsersBid(auction.auctionId, bidderAName, bidderAUUID, auction.stock, auction.currentPrice, Instant.now()))
      expectMsgPF() {
        case BidRejectedReply(_, BidRejectionReason.AUCTION_NOT_YET_STARTED) => ()
      }
    }

    "reject a bid made by it's owner" in {
      expectNoMsg(5.seconds) // Let the auction start

      auctionActor ! PlaceBid(UsersBid(auction.auctionId, sellerAName, sellerAUUID, auction.stock, auction.currentPrice, Instant.now()))
      expectMsgPF(10.seconds) {
        case BidRejectedReply(_, BidRejectionReason.SELF_BIDDING) => ()
      }
    }

    // TODO Bid on a seller's account locked
    // TODO Bid on a bidder's account locked

    "reject a bid with a quantity lower than 1" in {
      auctionActor ! PlaceBid(UsersBid(auction.auctionId, bidderAName, bidderAUUID, 0, auction.currentPrice, Instant.now()))
      expectMsgPF(10.seconds) {
        case BidRejectedReply(_, BidRejectionReason.WRONG_REQUESTED_QTY) => ()
      }
    }

    "reject a bid with a quantity higher than the available stock" in {
      auctionActor ! PlaceBid(UsersBid(auction.auctionId, bidderAName, bidderAUUID, auction.stock + 1, auction.currentPrice, Instant.now()))
      expectMsgPF(10.seconds) {
        case BidRejectedReply(_, BidRejectionReason.NOT_ENOUGH_STOCK) => ()
      }
    }

    "reject a bid with a price different from the auction's current price" in {
      auctionActor ! PlaceBid(UsersBid(auction.auctionId, bidderAName, bidderAUUID, auction.stock, auction.currentPrice + 1.0, Instant.now()))
      expectMsgPF(10.seconds) {
        case BidRejectedReply(_, BidRejectionReason.WRONG_BID_PRICE) => ()
      }
    }

    "accept a bid of the auction's stock value and close the auction" in {
      auctionActor ! PlaceBid(UsersBid(auction.auctionId, bidderAName, bidderAUUID, auction.stock, auction.currentPrice, Instant.now()))
      expectMsgPF(10.seconds) {
        case AuctionClosedReply(AuctionReason.BID_NO_REMAINING_STOCK) => ()
      }
    }

    "be in CLOSED and SOLD state" in {
      auctionActor ! GetCurrentState
      val expectedBidEssentials = List(
        (bidderAUUID, 10, auction.currentPrice, auction.currentPrice, true, false, false)
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
      auctionActor ! PlaceBid(UsersBid(auction.auctionId, bidderAName, bidderAUUID, auction.stock, auction.currentPrice, Instant.now()))
      expectMsgPF() {
        case BidRejectedReply(_, BidRejectionReason.AUCTION_HAS_ENDED) => ()
      }
    }

    // TODO ADD tests to check if a new auction has been created with the remaining stock

    //    "NOT be CLOSED after receiving a bid for a quantity lower than the available stock" in {
    //      val auctionActor = AuctionActor.createAuctionActor()
    //      val scheduledFixedPriceAuction = getScheduledFixedPriceAuction
    //
    //      auctionActor ! ScheduleAuction(scheduledFixedPriceAuction)
    //      expectNoMsg(10.seconds)
    //      auctionActor ! PlaceBid(UsersBid(auction.auctionId, bidderAName, bidderAUUID, scheduledFixedPriceAuction.stock-1, scheduledFixedPriceAuction.currentPrice, Instant.now()))
    //      expectMsg(10.seconds, BidPlacedReply)
    //    }
  }
}
