package actors

import java.time.Instant
import java.util.UUID

import actors.auction.AuctionActor
import actors.auction.AuctionActor._
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import cqrs.UsersBid
import cqrs.commands.{PlaceBid, ScheduleAuction}
import models.{Auction, AuctionReason, AuctionType, BidRejectionReason}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

/**
  * Created by Francois FERRARI on 17/05/2017
  */
class FixedPriceAuctionActorSpec() extends TestKit(ActorSystem("AuctionActorSpec"))
  with AuctionActorCommonsSpec
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  def getScheduledFixedPriceAuction = Auction(
    auctionId = UUID.randomUUID(),
    clonedFromAuctionId = None,
    clonedToAuctionId = None,
    cloneParameters = None,
    sellerId = sellerAUUID,
    typeId = UUID.randomUUID(),
    listedTimeId = UUID.randomUUID(),
    AuctionType.FIXED_PRICE,
    title = "Eiffel tower",
    description = "",
    year = 2010,
    areaId = UUID.randomUUID(),
    topicIds = Nil,
    options = Nil,
    matchedId = None,
    bids = Nil,
    startPrice = 0.10,
    currentPrice = 0.10,
    bidIncrement = 0.10,
    reservePrice = None,
    stock = 10,
    originalStock = 10,
    startsAt = instantNow.plusSeconds(5),
    suspendedAt = None,
    endsAt = instantNow.plusSeconds(60 * 60 * 24),
    hasAutomaticRenewal = true,
    hasTimeExtension = true,
    renewalCount = 0,
    watchersCount = 0,
    visitorsCount = 0,
    currency = "EUR",
    slug = None,
    pictures = Nil,
    closedBy = None,
    closedAt = None,
    instantNow
  )

  "A scheduled FIXED PRICE auction" should {

    val auctionActor = AuctionActor.createAuctionActor()
    val auction = getScheduledFixedPriceAuction
    auctionActor ! ScheduleAuction(auction)

    "reject a bid on an auction not yet started" in {
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
