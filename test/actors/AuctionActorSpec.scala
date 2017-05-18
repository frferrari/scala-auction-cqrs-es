package actors

import java.time.Instant
import java.util.UUID

import actors.AuctionActor._
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import cqrs.UsersBid
import cqrs.commands.{PlaceBid, ScheduleAuction}
import models.{Auction, AuctionType, BidRejectionReason}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

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

  def getScheduledAuction = Auction(
    UUID.randomUUID(),
    None, None, None,
    sellerAUUID,
    UUID.randomUUID(), UUID.randomUUID(),
    AuctionType.AUCTION,
    "Eiffel tower", "", 2010,
    UUID.randomUUID(), Nil, Nil, None,
    Nil,
    0.10, 0.10, 0.10, None,
    1, 1,
    instantNow.plusSeconds(5), None, instantNow.plusSeconds(60 * 60 * 24),
    hasAutomaticRenewal = true, hasTimeExtension = true,
    0, 0, 0,
    "EUR",
    None, Nil,
    None, None,
    instantNow
  )

  "A scheduled AUCTION" should {

    val auctionActor = AuctionActor.createAuctionActor()
    val auction = getScheduledAuction
    auctionActor ! ScheduleAuction(auction)

    "reject a bid on an auction not yet started" in {
      expectMsg(AuctionScheduledReply)
      auctionActor ! PlaceBid(UsersBid(UUID.randomUUID(), bidderAName, bidderAUUID, auction.stock, auction.currentPrice, Instant.now()))
      expectMsgPF() {
        case BidRejectedReply(_, BidRejectionReason.AUCTION_NOT_YET_STARTED) => ()
      }
    }

    "reject a bid made by it's owner" in {
      expectNoMsg(10.seconds) // Let the auction start
      auctionActor ! PlaceBid(UsersBid(UUID.randomUUID(), sellerAName, sellerAUUID, auction.stock, auction.currentPrice, Instant.now()))
      expectMsgPF(10.seconds) {
        case BidRejectedReply(_, BidRejectionReason.SELF_BIDDING) => ()
      }
    }

    // TODO Bid on a seller's account locked
    // TODO Bid on a bidder's account locked

    "reject a bid with a quantity lower than 1" in {
      auctionActor ! PlaceBid(UsersBid(UUID.randomUUID(), bidderAName, bidderAUUID, 0, auction.currentPrice, Instant.now()))
      expectMsgPF(10.seconds) {
        case BidRejectedReply(_, BidRejectionReason.WRONG_REQUESTED_QTY) => ()
      }
    }

    "reject a bid with a price lower than the auction's current price" in {
      auctionActor ! PlaceBid(UsersBid(UUID.randomUUID(), bidderAName, bidderAUUID, auction.stock, auction.currentPrice-0.01, Instant.now()))
      expectMsgPF(10.seconds) {
        case BidRejectedReply(_, BidRejectionReason.BID_BELOW_ALLOWED_MIN) => ()
      }
    }

    "accept a bid" in {
      val auctionActor = AuctionActor.createAuctionActor()

      auctionActor ! ScheduleAuction(auction)
      expectMsg(AuctionScheduledReply)
      expectNoMsg(10.seconds) // Let the auction start
      auctionActor ! PlaceBid(UsersBid(UUID.randomUUID(), bidderAName, bidderAUUID, 1, 1.00, Instant.now()))
      expectMsg(10.seconds, BidPlacedReply)
    }
  }
}
