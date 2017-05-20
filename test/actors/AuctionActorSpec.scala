package actors

import java.time.Instant
import java.util.UUID

import actors.auction.AuctionActor
import actors.auction.AuctionActor._
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import cqrs.UsersBid
import cqrs.commands.{PlaceBid, ScheduleAuction}
import models.{Auction, AuctionType, BidRejectionReason}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import play.api.Logger

import scala.concurrent.duration._

/**
  * Created by Francois FERRARI on 17/05/2017
  */
class AuctionActorSpec() extends TestKit(ActorSystem("AuctionActorSpec"))
  with AuctionActorCommonsSpec
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  def getScheduledAuction(startPrice: BigDecimal, bidIncrement: BigDecimal, lastsSeconds: Long, reservePrice: Option[BigDecimal] = None) = Auction(
    auctionId = UUID.randomUUID(),
    None, None, None,
    sellerId = sellerAUUID,
    typeId = UUID.randomUUID(), listedTimeId = UUID.randomUUID(),
    AuctionType.AUCTION,
    "Eiffel tower", "", 2010,
    areaId = UUID.randomUUID(), topicIds = Nil, options = Nil, matchedId = None,
    bids = Nil,
    startPrice = startPrice, currentPrice = startPrice, bidIncrement = bidIncrement, reservePrice = reservePrice,
    stock = 1, originalStock = 1,
    instantNow.plusSeconds(5), None, instantNow.plusSeconds(lastsSeconds),
    hasAutomaticRenewal = true,
    hasTimeExtension = false,
    renewalCount = 0, watchersCount = 0, visitorsCount = 0,
    "EUR",
    slug = None, pictures = Nil,
    closedBy = None, closedAt = None,
    instantNow
  )

  "An AUCTION w/o reserve price" should {

    val auctionActor = AuctionActor.createAuctionActor()
    val auction = getScheduledAuction(0.10, 0.10, 60)
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

    "reject a bid with a price lower than the auction's current price" in {
      auctionActor ! PlaceBid(UsersBid(auction.auctionId, bidderAName, bidderAUUID, auction.stock, auction.currentPrice - 0.01, Instant.now()))
      expectMsgPF() {
        case BidRejectedReply(_, BidRejectionReason.BID_BELOW_ALLOWED_MIN) => ()
      }
    }

    "accept a first bid from bidderA" in {
      auctionActor ! PlaceBid(UsersBid(auction.auctionId, bidderAName, bidderAUUID, 1, 4.00, Instant.now()))
      expectMsg(BidPlacedWithInfoReply(auction.auctionId, auction.stock, auction.currentPrice, 1, Some(bidderAUUID), Some(4.00)))
    }

    "reject a bid from a user who's the current winner (bidderA) with a bid value lower than this user's max price" in {
      auctionActor ! PlaceBid(UsersBid(auction.auctionId, bidderAName, bidderAUUID, 1, 2.00, Instant.now()))
      expectMsgPF() {
        case BidRejectedReply(_, BidRejectionReason.HIGHEST_BIDDER_BIDS_BELOW_HIS_MAX_PRICE) => ()
      }
    }

    //
    //    "accept a bid from a user who's the current winner (bidderA) and who wants to raise his maximum price" in {
    //      auctionActor ! PlaceBid(UsersBid(auction.auctionId, bidderAName, bidderAUUID, 1, 6.00, Instant.now()))
    //      expectMsgPF() {
    //        case _: BidPlacedWithInfoReply => ()
    //      }
    //    }
    //
    //    "accept a bid from bidderB with a bid value lower than the current highest bidder, should raise the currentPrice to bidderB's bid price" in {
    //      auctionActor ! PlaceBid(UsersBid(auction.auctionId, bidderBName, bidderBUUID, 1, 4.00, Instant.now()))
    //      expectMsgPF() {
    //        case bpr: BidPlacedWithInfoReply =>
    //          Logger.info(s"--------- $bpr")
    //          ()
    //      }
    //    }
  }
}
