package actors

import java.time.Instant
import java.util.UUID

import actors.auction.AuctionActor
import actors.auction.AuctionActor.{AuctionStartedReply, BidPlacedReply, BidRejectedReply}
import actors.user.UserActor
import actors.user.UserActor.UserRegisteredReply
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import cqrs.UsersBid
import cqrs.commands._
import models.{BidRejectionReason, UserReason}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

/**
  * Created by Francois FERRARI on 24/05/2017
  */
class CantBidOnAuctionWhoseSellerIsLockedSpec
  extends TestKit(ActorSystem("AuctionActorSpec"))
    with ActorCommonsSpec
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  implicit val emailUnicityMock = new EmailUnicityMock

  "An AUCTION" should {

    "reject a bid on an auction whose seller is Locked" in {
      val seller = makeUser("contact@pluto.space", "hhgg", "Robert", "John")
      val (sellerName, sellerActor) = (seller.nickName, UserActor.createUserActor(seller))

      // Register and Lock the seller
      sellerActor ! RegisterUser(seller, Instant.now())
      expectMsg(UserRegisteredReply)

      sellerActor ! LockUser(seller.userId, UserReason.UNPAID_INVOICE, UUID.randomUUID(), Instant.now())
      expectNoMsg(2.seconds)

      val auction = makeAuction(
        startPrice = 0.10,
        bidIncrement = 0.10,
        startsAt = Instant.now(),
        lastsSeconds = 30,
        hasAutomaticRenewal = false,
        hasTimeExtension = false,
        seller.userId
      )
      val auctionActor = AuctionActor.createAuctionActor(auction)
      auctionActor ! StartAuction(auction)
      expectMsg(AuctionStartedReply)

      // Place a bid which should be rejected (seller LOCKED)
      auctionActor ! PlaceBid(UsersBid(UUID.randomUUID(), bidderAName, bidderAUUID, 1, 1.00, Instant.now()))
      expectMsgPF() {
        case BidRejectedReply(_, BidRejectionReason.SELLER_LOCKED) => ()
      }

      // UNLOCK the seller
      sellerActor ! UnlockUser(seller.userId, Instant.now())
      expectNoMsg(2.seconds)

      // Place a bid which should be accepted (seller UNLOCKED)
      auctionActor ! PlaceBid(UsersBid(UUID.randomUUID(), bidderAName, bidderAUUID, 1, 1.00, Instant.now()))
      expectMsg(BidPlacedReply)

      // LOCK the seller again
      sellerActor ! LockUser(seller.userId, UserReason.UNPAID_INVOICE, UUID.randomUUID(), Instant.now())
      expectNoMsg(2.seconds)

      // Place a bid which should be rejected (seller LOCKED)
      auctionActor ! PlaceBid(UsersBid(UUID.randomUUID(), bidderAName, bidderAUUID, 1, 1.00, Instant.now()))
      expectMsgPF() {
        case BidRejectedReply(_, BidRejectionReason.SELLER_LOCKED) => ()
      }
    }
  }
}
