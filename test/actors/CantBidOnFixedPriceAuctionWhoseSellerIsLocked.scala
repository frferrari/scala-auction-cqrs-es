package actors

import java.time.Instant
import java.util.UUID

import actors.auction.AuctionActor
import actors.auction.AuctionActor._
import actors.auction.fsm.{ClosedState, FinishedAuction}
import actors.user.UserActor
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import cqrs.UsersBid
import cqrs.commands._
import models.{AuctionReason, BidRejectionReason, UserReason}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

/**
  * Created by Francois FERRARI on 24/05/2017
  */
class CantBidOnFixedPriceAuctionWhoseSellerIsLocked extends TestKit(ActorSystem("AuctionActorSpec")) with ActorCommonsSpec
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "A Fixed Price AUCTION" should {

    "reject a bid on an auction whose seller is Locked" in {
      val seller = makeUser("contact@pluto.space", "hhgg", "Robert", "John")
      val (sellerName, sellerActor) = (seller.nickName, UserActor.createUserActor(seller))

      // Register and Lock the seller
      sellerActor ! RegisterUser(seller, Instant.now())
      sellerActor ! LockUser(seller.userId, UserReason.UNPAID_INVOICE, UUID.randomUUID(), Instant.now())
      expectNoMsg(2.seconds)

      val auction = makeFixedPriceAuction(
        startPrice = 0.10,
        startsAt = Instant.now(),
        lastsSeconds = 30,
        stock = 10,
        hasAutomaticRenewal = false,
        sellerId = seller.userId
      )
      val auctionActor = AuctionActor.createAuctionActor(auction)
      auctionActor ! StartAuction(auction)
      expectMsg(AuctionStartedReply)
      expectNoMsg(2.seconds)

      // Place a bid which should be rejected (seller LOCKED)
      auctionActor ! PlaceBid(UsersBid(UUID.randomUUID(), bidderAName, bidderAUUID, 10, 0.10, Instant.now()))
      expectMsgPF() {
        case BidRejectedReply(_, BidRejectionReason.SELLER_LOCKED) => ()
      }

      // UNLOCK the seller
      sellerActor ! UnlockUser(seller.userId, Instant.now())
      expectNoMsg(2.seconds)

      // Place a bid which should be accepted (seller UNLOCKED)
      auctionActor ! PlaceBid(UsersBid(UUID.randomUUID(), bidderAName, bidderAUUID, 10, 0.10, Instant.now()))
      expectMsg(AuctionClosedReply(AuctionReason.BID_NO_REMAINING_STOCK))

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

      expectNoMsg(2.seconds)
    }
  }
}
