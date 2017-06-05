package actors.auction

import java.time.Instant

import actors.ActorCommonsSpec
import actors.auction.AuctionActor._
import actors.auction.fsm.{ActiveAuction, ClosedState, FinishedAuction, StartedState}
import actors.auctionSupervisor.AuctionSupervisor
import actors.user.UserActor.UserRegisteredReply
import actors.userSupervisor.UserSupervisor
import actors.userUnicity.UserUnicityActor
import actors.userUnicity.UserUnicityActor.UserUnicityListReply
import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.testkit.{ImplicitSender, TestKit}
import cqrs.UsersBid
import cqrs.commands._
import models.BidRejectionReason
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import play.api.inject.BindingKey
import play.api.inject.guice.GuiceApplicationBuilder

import scala.concurrent.duration._

/**
  * Created by Francois FERRARI on 17/05/2017
  */
class AuctionActorSpec4() extends TestKit(ActorSystem("AuctionSystem"))
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
  val bidderB = makeUser("bidderB@pluto.space", "bidderB", "BidderB", "John")

  val auction = makeAuction(
    startPrice = 0.10,
    bidIncrement = 0.10,
    startsAt = Instant.now().plusSeconds(8), // mandatory as we check that it is forbidden to place a bid on a scheduled auction
    lastsSeconds = 20,
    hasAutomaticRenewal = false,
    hasTimeExtension = false,
    sellerId = seller.userId
  )

  "An AUCTION W/O reserve price W/2 bidders" should {

    "successfully create a User (Seller)" in {
      userSupervisorActorRef ! CreateUser(seller)
      expectMsg(UserRegisteredReply)
    }

    "successfully create a User (bidderA)" in {
      userSupervisorActorRef ! CreateUser(bidderA)
      expectMsg(UserRegisteredReply)
    }

    "successfully create a User (bidderB)" in {
      userSupervisorActorRef ! CreateUser(bidderB)
      expectMsg(UserRegisteredReply)
    }

    "successfully create an auction in ScheduledState" in {
      auctionSupervisorActorRef ! CreateAuction(auction)
      expectMsg(AuctionScheduledReply)
    }

    "ensure that the UserUnicityActor is ready" in {
      userUnicityActorRef ! GetUserUnicityList
      expectMsgPF(5.seconds) {
        case (UserUnicityListReply(userUnicityList)) if userUnicityList.length == 3 => ()
      }
    }

    "reject a bid placed on a ScheduledAuction" in {
      getAuctionActorSelection(auction.auctionId) ! PlaceBid(UsersBid(auction.auctionId, bidderA.nickName, bidderA.userId, auction.stock, auction.currentPrice, Instant.now()))
      expectMsgPF() {
        case BidRejectedReply(_, BidRejectionReason.AUCTION_NOT_YET_STARTED) => ()
      }
    }

    "reject a bid made by the auction owner" in {
      expectNoMsg(secondsToWaitForAuctionStart(auction).seconds) // Let the auction start
      getAuctionActorSelection(auction.auctionId) ! PlaceBid(UsersBid(auction.auctionId, seller.nickName, seller.userId, auction.stock, auction.currentPrice, Instant.now()))
      expectMsgPF(10.seconds) {
        case BidRejectedReply(_, BidRejectionReason.SELF_BIDDING) => ()
      }
    }

    "reject a bid with a quantity lower than 1" in {
      getAuctionActorSelection(auction.auctionId) ! PlaceBid(UsersBid(auction.auctionId, bidderA.nickName, bidderA.userId, 0, auction.currentPrice, Instant.now()))
      expectMsgPF(10.seconds) {
        case BidRejectedReply(_, BidRejectionReason.WRONG_REQUESTED_QTY) => ()
      }
    }

    "reject a bid with a price lower than the auction's current price" in {
      getAuctionActorSelection(auction.auctionId) ! PlaceBid(UsersBid(auction.auctionId, bidderA.nickName, bidderA.userId, auction.stock, auction.currentPrice - 0.01, Instant.now()))
      expectMsgPF() {
        case BidRejectedReply(_, BidRejectionReason.BID_BELOW_ALLOWED_MIN) => ()
      }
    }

    "accept a first bid from bidderA" in {
      getAuctionActorSelection(auction.auctionId) ! PlaceBid(UsersBid(auction.auctionId, bidderA.nickName, bidderA.userId, 1, 4.00, Instant.now()))
      expectMsg(BidPlacedReply)

      getAuctionActorSelection(auction.auctionId) ! GetCurrentState
      expectMsgPF() {
        case CurrentStateReply(StartedState, ActiveAuction(activeAuction))
          if activeAuction.currentPrice == activeAuction.startPrice &&
            activeAuction.bids.length == 1 &&
            activeAuction.bids.head.bidPrice == activeAuction.startPrice &&
            activeAuction.bids.head.bidMaxPrice == 4.0 &&
            activeAuction.bids.head.bidderId == bidderA.userId
        => ()
      }
    }

    "reject a bid from a user who's the current winner (bidderA) with a bid value lower than this user's max price" in {
      getAuctionActorSelection(auction.auctionId) ! PlaceBid(UsersBid(auction.auctionId, bidderA.nickName, bidderA.userId, 1, 2.00, Instant.now()))
      expectMsgPF() {
        case BidRejectedReply(_, BidRejectionReason.HIGHEST_BIDDER_BIDS_BELOW_HIS_MAX_PRICE) => ()
      }
    }

    "accept a bid from a user who's the current winner (bidderA) and who wants to raise his maximum price" in {
      getAuctionActorSelection(auction.auctionId) ! PlaceBid(UsersBid(auction.auctionId, bidderA.nickName, bidderA.userId, 1, 6.00, Instant.now()))
      expectMsg(BidPlacedReply)

      getAuctionActorSelection(auction.auctionId) ! GetCurrentState
      expectMsgPF() {
        case CurrentStateReply(StartedState, ActiveAuction(activeAuction))
          if activeAuction.currentPrice == activeAuction.startPrice &&
            activeAuction.bids.length == 2 &&
            activeAuction.bids.head.bidPrice == activeAuction.startPrice &&
            activeAuction.bids.head.bidMaxPrice == 6.0 &&
            activeAuction.bids.head.bidderId == bidderA.userId
        => ()
      }
    }

    "accept a bid from bidderB with a bid value lower than the current highest bidder, should raise the currentPrice to bidderB's bid price (4.00), bidderA is still the winner" in {
      getAuctionActorSelection(auction.auctionId) ! PlaceBid(UsersBid(auction.auctionId, bidderB.nickName, bidderB.userId, 1, 4.00, Instant.now()))
      expectMsg(BidPlacedReply)

      getAuctionActorSelection(auction.auctionId) ! GetCurrentState
      val expectedBidEssentials = List(
        (bidderA.userId, 1, 4.0, 6.0, true, true, false),
        (bidderB.userId, 1, 4.0, 6.0, true, false, false),
        (bidderA.userId, 1, 0.1, 6.0, false, false, false),
        (bidderA.userId, 1, 0.1, 4.0, true, false, false)
      )
      expectMsgPF() {
        case CurrentStateReply(StartedState, ActiveAuction(activeAuction))
          if activeAuction.currentPrice == 4.00 &&
            activeAuction.bids.head.bidPrice == 4.00 &&
            activeAuction.bids.head.bidMaxPrice == 6.0 &&
            activeAuction.bids.head.bidderId == bidderA.userId &&
            bidEssentials(activeAuction.bids) == expectedBidEssentials
        => ()
      }
    }

    "accept a bid from bidderB with a bid value lower than the current highest bidder, should raise the currentPrice to bidderB's bid price (5.00), bidderA is still the winner" in {
      getAuctionActorSelection(auction.auctionId) ! PlaceBid(UsersBid(auction.auctionId, bidderB.nickName, bidderB.userId, 1, 5.00, Instant.now()))
      expectMsg(BidPlacedReply)

      getAuctionActorSelection(auction.auctionId) ! GetCurrentState
      val expectedBidEssentials = List(
        (bidderA.userId, 1, 5.0, 6.0, true, true, false),
        (bidderB.userId, 1, 5.0, 6.0, true, false, false),
        (bidderA.userId, 1, 4.0, 6.0, true, true, false),
        (bidderB.userId, 1, 4.0, 6.0, true, false, false),
        (bidderA.userId, 1, 0.1, 6.0, false, false, false),
        (bidderA.userId, 1, 0.1, 4.0, true, false, false)
      )
      expectMsgPF() {
        case CurrentStateReply(StartedState, ActiveAuction(activeAuction))
          if activeAuction.currentPrice == 5.00 &&
            activeAuction.bids.head.bidPrice == 5.00 &&
            activeAuction.bids.head.bidMaxPrice == 6.0 &&
            activeAuction.bids.head.bidderId == bidderA.userId &&
            bidEssentials(activeAuction.bids) == expectedBidEssentials
        => ()
      }
    }

    "accept a bid from bidderB with a bid value lower than the current highest bidder, should raise the currentPrice to bidderB's bid price (6.00), bidderA is still the winner" in {
      getAuctionActorSelection(auction.auctionId) ! PlaceBid(UsersBid(auction.auctionId, bidderB.nickName, bidderB.userId, 1, 6.00, Instant.now()))
      expectMsg(BidPlacedReply)

      getAuctionActorSelection(auction.auctionId) ! GetCurrentState
      val expectedBidEssentials = List(
        (bidderA.userId, 1, 6.0, 6.0, true, true, false),
        (bidderB.userId, 1, 6.0, 6.0, true, false, false),
        (bidderA.userId, 1, 5.0, 6.0, true, true, false),
        (bidderB.userId, 1, 5.0, 6.0, true, false, false),
        (bidderA.userId, 1, 4.0, 6.0, true, true, false),
        (bidderB.userId, 1, 4.0, 6.0, true, false, false),
        (bidderA.userId, 1, 0.1, 6.0, false, false, false),
        (bidderA.userId, 1, 0.1, 4.0, true, false, false)
      )
      expectMsgPF() {
        case CurrentStateReply(StartedState, ActiveAuction(activeAuction))
          if activeAuction.currentPrice == 6.00 &&
            activeAuction.bids.head.bidPrice == 6.00 &&
            activeAuction.bids.head.bidMaxPrice == 6.0 &&
            activeAuction.bids.head.bidderId == bidderA.userId &&
            bidEssentials(activeAuction.bids) == expectedBidEssentials
        => ()
      }
    }

    "accept a bid from bidderB with a bid value lower than the current highest bidder, should raise the currentPrice to bidderB's bid price (6.20), bidderB is the new winner" in {
      getAuctionActorSelection(auction.auctionId) ! PlaceBid(UsersBid(auction.auctionId, bidderB.nickName, bidderB.userId, 1, 6.20, Instant.now()))
      expectMsg(BidPlacedReply)

      getAuctionActorSelection(auction.auctionId) ! GetCurrentState
      val expectedBidEssentials = List(
        (bidderB.userId, 1, 6.10, 6.20, true, false, false),
        (bidderA.userId, 1, 6.0, 6.0, true, true, false),
        (bidderB.userId, 1, 6.0, 6.0, true, false, false),
        (bidderA.userId, 1, 5.0, 6.0, true, true, false),
        (bidderB.userId, 1, 5.0, 6.0, true, false, false),
        (bidderA.userId, 1, 4.0, 6.0, true, true, false),
        (bidderB.userId, 1, 4.0, 6.0, true, false, false),
        (bidderA.userId, 1, 0.1, 6.0, false, false, false),
        (bidderA.userId, 1, 0.1, 4.0, true, false, false)
      )
      expectMsgPF() {
        case CurrentStateReply(StartedState, ActiveAuction(activeAuction))
          if activeAuction.currentPrice == 6.10 &&
            activeAuction.bids.head.bidPrice == 6.10 &&
            activeAuction.bids.head.bidMaxPrice == 6.20 &&
            activeAuction.bids.head.bidderId == bidderB.userId &&
            bidEssentials(activeAuction.bids) == expectedBidEssentials
        => ()
      }
    }

    "Reject a bid after auction has ended" in {
      expectNoMsg(secondsToWaitForAuctionEnd(auction).seconds)

      getAuctionActorSelection(auction.auctionId) ! PlaceBid(UsersBid(auction.auctionId, bidderB.nickName, bidderB.userId, 1, 6.80, Instant.now()))
      expectMsgPF() {
        case BidRejectedReply(_, BidRejectionReason.AUCTION_HAS_ENDED) => ()
      }
    }

    "be in CLOSED state with bidderB as the auction's winner" in {
      getAuctionActorSelection(auction.auctionId) ! GetCurrentState

      val expectedBidEssentials = List(
        (bidderB.userId, 1, 6.10, 6.20, true, false, false),
        (bidderA.userId, 1, 6.0, 6.0, true, true, false),
        (bidderB.userId, 1, 6.0, 6.0, true, false, false),
        (bidderA.userId, 1, 5.0, 6.0, true, true, false),
        (bidderB.userId, 1, 5.0, 6.0, true, false, false),
        (bidderA.userId, 1, 4.0, 6.0, true, true, false),
        (bidderB.userId, 1, 4.0, 6.0, true, false, false),
        (bidderA.userId, 1, 0.1, 6.0, false, false, false),
        (bidderA.userId, 1, 0.1, 4.0, true, false, false)
      )
      expectMsgPF() {
        case CurrentStateReply(ClosedState, FinishedAuction(finishedAuction))
          if finishedAuction.currentPrice == 6.10 &&
            finishedAuction.bids.head.bidPrice == 6.10 &&
            finishedAuction.bids.head.bidMaxPrice == 6.20 &&
            finishedAuction.bids.head.bidderId == bidderB.userId &&
            bidEssentials(finishedAuction.bids) == expectedBidEssentials &&
            finishedAuction.closedBy.isDefined &&
            finishedAuction.isSold
        => ()
      }

      getAuctionActorSelection(auction.auctionId) ! PoisonPill
      expectNoMsg(2.seconds)
    }
  }
}
