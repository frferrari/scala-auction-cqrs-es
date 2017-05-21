package actors

import java.time.Instant

import actors.auction.AuctionActor
import actors.auction.AuctionActor._
import actors.auction.fsm.{ActiveAuction, ClosedState, FinishedAuction, StartedState}
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import cqrs.UsersBid
import cqrs.commands.{GetCurrentState, PlaceBid, ScheduleAuction}
import models.BidRejectionReason
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

/**
  * Created by Francois FERRARI on 17/05/2017
  */
class AuctionActorSpec4() extends TestKit(ActorSystem("AuctionActorSpec"))
  with AuctionActorCommonsSpec
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "An AUCTION W/O reserve price W/2 bidders" should {

    val auctionActor = AuctionActor.createAuctionActor(Some("test2"))
    val auction = getScheduledAuction(
      startPrice = 0.10,
      bidIncrement = 0.10,
      startsAt = Instant.now(),
      lastsSeconds = 20,
      hasAutomaticRenewal = false,
      hasTimeExtension = false
    )

    "start an auction in ScheduledState" in {
      auctionActor ! ScheduleAuction(auction)
      expectMsg(AuctionScheduledReply)
      auctionActor ! PlaceBid(UsersBid(auction.auctionId, bidderAName, bidderAUUID, auction.stock, auction.currentPrice, Instant.now()))
      expectMsgPF() {
        case BidRejectedReply(_, BidRejectionReason.AUCTION_NOT_YET_STARTED) => ()
      }
    }

    "reject a bid made by it's owner" in {
      expectNoMsg(10.seconds) // Let the auction start
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

    "reject a bid with a price lower than the auction's current price" in {
      auctionActor ! PlaceBid(UsersBid(auction.auctionId, bidderAName, bidderAUUID, auction.stock, auction.currentPrice - 0.01, Instant.now()))
      expectMsgPF() {
        case BidRejectedReply(_, BidRejectionReason.BID_BELOW_ALLOWED_MIN) => ()
      }
    }

    "accept a first bid from bidderA" in {
      auctionActor ! PlaceBid(UsersBid(auction.auctionId, bidderAName, bidderAUUID, 1, 4.00, Instant.now()))
      expectMsg(BidPlacedReply)

      auctionActor ! GetCurrentState
      expectMsgPF() {
        case CurrentStateReply(StartedState, ActiveAuction(activeAuction))
          if activeAuction.currentPrice == activeAuction.startPrice &&
            activeAuction.bids.length == 1 &&
            activeAuction.bids.head.bidPrice == activeAuction.startPrice &&
            activeAuction.bids.head.bidMaxPrice == 4.0 &&
            activeAuction.bids.head.bidderId == bidderAUUID
        => ()
      }
    }

    "reject a bid from a user who's the current winner (bidderA) with a bid value lower than this user's max price" in {
      auctionActor ! PlaceBid(UsersBid(auction.auctionId, bidderAName, bidderAUUID, 1, 2.00, Instant.now()))
      expectMsgPF() {
        case BidRejectedReply(_, BidRejectionReason.HIGHEST_BIDDER_BIDS_BELOW_HIS_MAX_PRICE) => ()
      }
    }

    "accept a bid from a user who's the current winner (bidderA) and who wants to raise his maximum price" in {
      auctionActor ! PlaceBid(UsersBid(auction.auctionId, bidderAName, bidderAUUID, 1, 6.00, Instant.now()))
      expectMsg(BidPlacedReply)

      auctionActor ! GetCurrentState
      expectMsgPF() {
        case CurrentStateReply(StartedState, ActiveAuction(activeAuction))
          if activeAuction.currentPrice == activeAuction.startPrice &&
            activeAuction.bids.length == 2 &&
            activeAuction.bids.head.bidPrice == activeAuction.startPrice &&
            activeAuction.bids.head.bidMaxPrice == 6.0 &&
            activeAuction.bids.head.bidderId == bidderAUUID
        => ()
      }
    }

    "accept a bid from bidderB with a bid value lower than the current highest bidder, should raise the currentPrice to bidderB's bid price (4.00), bidderA is still the winner" in {
      auctionActor ! PlaceBid(UsersBid(auction.auctionId, bidderBName, bidderBUUID, 1, 4.00, Instant.now()))
      expectMsg(BidPlacedReply)

      auctionActor ! GetCurrentState
      val expectedBidEssentials = List(
        (bidderAUUID, 1, 4.0, 6.0, true, true, false),
        (bidderBUUID, 1, 4.0, 6.0, true, false, false),
        (bidderAUUID, 1, 0.1, 6.0, false, false, false),
        (bidderAUUID, 1, 0.1, 4.0, true, false, false)
      )
      expectMsgPF() {
        case CurrentStateReply(StartedState, ActiveAuction(activeAuction))
          if activeAuction.currentPrice == 4.00 &&
            activeAuction.bids.head.bidPrice == 4.00 &&
            activeAuction.bids.head.bidMaxPrice == 6.0 &&
            activeAuction.bids.head.bidderId == bidderAUUID &&
            bidEssentials(activeAuction.bids) == expectedBidEssentials
        => ()
      }
    }

    "accept a bid from bidderB with a bid value lower than the current highest bidder, should raise the currentPrice to bidderB's bid price (5.00), bidderA is still the winner" in {
      auctionActor ! PlaceBid(UsersBid(auction.auctionId, bidderBName, bidderBUUID, 1, 5.00, Instant.now()))
      expectMsg(BidPlacedReply)

      auctionActor ! GetCurrentState
      val expectedBidEssentials = List(
        (bidderAUUID, 1, 5.0, 6.0, true, true, false),
        (bidderBUUID, 1, 5.0, 6.0, true, false, false),
        (bidderAUUID, 1, 4.0, 6.0, true, true, false),
        (bidderBUUID, 1, 4.0, 6.0, true, false, false),
        (bidderAUUID, 1, 0.1, 6.0, false, false, false),
        (bidderAUUID, 1, 0.1, 4.0, true, false, false)
      )
      expectMsgPF() {
        case CurrentStateReply(StartedState, ActiveAuction(activeAuction))
          if activeAuction.currentPrice == 5.00 &&
            activeAuction.bids.head.bidPrice == 5.00 &&
            activeAuction.bids.head.bidMaxPrice == 6.0 &&
            activeAuction.bids.head.bidderId == bidderAUUID &&
            bidEssentials(activeAuction.bids) == expectedBidEssentials
        => ()
      }
    }

    "accept a bid from bidderB with a bid value lower than the current highest bidder, should raise the currentPrice to bidderB's bid price (6.00), bidderA is still the winner" in {
      auctionActor ! PlaceBid(UsersBid(auction.auctionId, bidderBName, bidderBUUID, 1, 6.00, Instant.now()))
      expectMsg(BidPlacedReply)

      auctionActor ! GetCurrentState
      val expectedBidEssentials = List(
        (bidderAUUID, 1, 6.0, 6.0, true, true, false),
        (bidderBUUID, 1, 6.0, 6.0, true, false, false),
        (bidderAUUID, 1, 5.0, 6.0, true, true, false),
        (bidderBUUID, 1, 5.0, 6.0, true, false, false),
        (bidderAUUID, 1, 4.0, 6.0, true, true, false),
        (bidderBUUID, 1, 4.0, 6.0, true, false, false),
        (bidderAUUID, 1, 0.1, 6.0, false, false, false),
        (bidderAUUID, 1, 0.1, 4.0, true, false, false)
      )
      expectMsgPF() {
        case CurrentStateReply(StartedState, ActiveAuction(activeAuction))
          if activeAuction.currentPrice == 6.00 &&
            activeAuction.bids.head.bidPrice == 6.00 &&
            activeAuction.bids.head.bidMaxPrice == 6.0 &&
            activeAuction.bids.head.bidderId == bidderAUUID &&
            bidEssentials(activeAuction.bids) == expectedBidEssentials
        => ()
      }
    }

    "accept a bid from bidderB with a bid value lower than the current highest bidder, should raise the currentPrice to bidderB's bid price (6.20), bidderB is the new winner" in {
      auctionActor ! PlaceBid(UsersBid(auction.auctionId, bidderBName, bidderBUUID, 1, 6.20, Instant.now()))
      expectMsg(BidPlacedReply)

      auctionActor ! GetCurrentState
      val expectedBidEssentials = List(
        (bidderBUUID, 1, 6.10, 6.20, true, false, false),
        (bidderAUUID, 1, 6.0, 6.0, true, true, false),
        (bidderBUUID, 1, 6.0, 6.0, true, false, false),
        (bidderAUUID, 1, 5.0, 6.0, true, true, false),
        (bidderBUUID, 1, 5.0, 6.0, true, false, false),
        (bidderAUUID, 1, 4.0, 6.0, true, true, false),
        (bidderBUUID, 1, 4.0, 6.0, true, false, false),
        (bidderAUUID, 1, 0.1, 6.0, false, false, false),
        (bidderAUUID, 1, 0.1, 4.0, true, false, false)
      )
      expectMsgPF() {
        case CurrentStateReply(StartedState, ActiveAuction(activeAuction))
          if activeAuction.currentPrice == 6.10 &&
            activeAuction.bids.head.bidPrice == 6.10 &&
            activeAuction.bids.head.bidMaxPrice == 6.20 &&
            activeAuction.bids.head.bidderId == bidderBUUID &&
            bidEssentials(activeAuction.bids) == expectedBidEssentials
        => ()
      }
    }

    "Reject a bid after auction has ended" in {
      expectNoMsg(secondsToWaitForAuctionEnd(auction).seconds)

      auctionActor ! PlaceBid(UsersBid(auction.auctionId, bidderBName, bidderBUUID, 1, 6.80, Instant.now()))
      expectMsgPF() {
        case BidRejectedReply(_, BidRejectionReason.AUCTION_HAS_ENDED) => ()
      }
    }

    "be in CLOSED state with bidderB as the auction's winner" in {
      auctionActor ! GetCurrentState
      val expectedBidEssentials = List(
        (bidderBUUID, 1, 6.10, 6.20, true, false, false),
        (bidderAUUID, 1, 6.0, 6.0, true, true, false),
        (bidderBUUID, 1, 6.0, 6.0, true, false, false),
        (bidderAUUID, 1, 5.0, 6.0, true, true, false),
        (bidderBUUID, 1, 5.0, 6.0, true, false, false),
        (bidderAUUID, 1, 4.0, 6.0, true, true, false),
        (bidderBUUID, 1, 4.0, 6.0, true, false, false),
        (bidderAUUID, 1, 0.1, 6.0, false, false, false),
        (bidderAUUID, 1, 0.1, 4.0, true, false, false)
      )
      expectMsgPF() {
        case CurrentStateReply(ClosedState, FinishedAuction(finishedAuction))
          if finishedAuction.currentPrice == 6.10 &&
            finishedAuction.bids.head.bidPrice == 6.10 &&
            finishedAuction.bids.head.bidMaxPrice == 6.20 &&
            finishedAuction.bids.head.bidderId == bidderBUUID &&
            bidEssentials(finishedAuction.bids) == expectedBidEssentials &&
            finishedAuction.closedBy.isDefined
        => ()
      }
    }
  }
}
