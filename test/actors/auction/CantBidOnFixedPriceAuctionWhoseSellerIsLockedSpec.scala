package actors.auction

import java.time.Instant
import java.util.UUID

import actors.ActorCommonsSpec
import actors.auction.AuctionActor._
import actors.auction.fsm.{ClosedState, FinishedAuction}
import actors.auctionSupervisor.AuctionSupervisor
import actors.user.UserActor.UserRegisteredReply
import actors.user.UserActorHelpers
import actors.userSupervisor.UserSupervisor
import actors.userUnicity.UserUnicityActor
import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.testkit.{ImplicitSender, TestKit}
import cqrs.UsersBid
import cqrs.commands._
import models.{AuctionReason, BidRejectionReason, UserReason}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import play.api.inject.BindingKey
import play.api.inject.guice.GuiceApplicationBuilder

import scala.concurrent.duration._

/**
  * Created by Francois FERRARI on 24/05/2017
  */
class CantBidOnFixedPriceAuctionWhoseSellerIsLockedSpec
  extends TestKit(ActorSystem("AuctionSystem"))
  with ActorCommonsSpec
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with UserActorHelpers {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  val app = new GuiceApplicationBuilder().build()
  val injector = app.injector
  val userUnicityActorRef = injector.instanceOf(BindingKey(classOf[ActorRef]).qualifiedWith(UserUnicityActor.name))
  val userSupervisorActorRef = system.actorOf(Props(new UserSupervisor(userUnicityActorRef)), name = UserSupervisor.name)
  val auctionSupervisorActorRef = system.actorOf(Props(new AuctionSupervisor()), name = AuctionSupervisor.name)

  val seller = makeUser("contact@pluto.space", "hhgg", "Robert", "John")
  val auction = makeFixedPriceAuction(
    startPrice = 0.10,
    startsAt = Instant.now(),
    lastsSeconds = 30,
    stock = 10,
    hasAutomaticRenewal = false,
    sellerId = seller.userId
  )

  "A Fixed Price AUCTION" should {

    "successfully create a User (Seller)" in {
      userSupervisorActorRef ! CreateUser(seller)
      expectMsg(UserRegisteredReply)
    }

    "successfully create an auction" in {
      auctionSupervisorActorRef ! CreateAuction(auction)
      expectMsg(AuctionStartedReply)
    }

    "successfully Lock a Seller" in {
      getUserActorSelection(seller.userId) ! LockUser(seller.userId, UserReason.UNPAID_INVOICE, UUID.randomUUID(), Instant.now())
      expectNoMsg(2.seconds) // Needed to allow the seller actor transition event to be processed by the auction actor
    }

    "reject a bid on an auction whose seller is Locked" in {
      getAuctionActorSelection(auction.auctionId) ! PlaceBid(UsersBid(UUID.randomUUID(), bidderAName, bidderAUUID, 10, 0.10, Instant.now()))
      expectMsgPF() {
        case BidRejectedReply(_, BidRejectionReason.SELLER_LOCKED) => ()
      }
    }

    "successfully Unlock a Seller" in {
      getUserActorSelection(seller.userId) ! UnlockUser(seller.userId, Instant.now())
      expectNoMsg(2.seconds) // Needed to allow the seller actor transition event to be processed by the auction actor
    }

    "accept a bid on an auction whose seller is NOT Locked" in {
      getAuctionActorSelection(auction.auctionId) ! PlaceBid(UsersBid(UUID.randomUUID(), bidderAName, bidderAUUID, 10, 0.10, Instant.now()))
      expectMsg(AuctionClosedReply(AuctionReason.BID_NO_REMAINING_STOCK))
    }

    "successfully check the auction state" in {
      getAuctionActorSelection(auction.auctionId) ! GetCurrentState
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

      // Before stopping the auction actor should send an Unsubscribe msg to the user actor
      getAuctionActorSelection(auction.auctionId) ! PoisonPill
      expectNoMsg(2.seconds)
    }
  }
}
