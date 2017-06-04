package actors.auction

import java.time.Instant

import actors.ActorCommonsSpec
import actors.auction.AuctionActor._
import actors.auction.fsm.{ActiveAuction, ClosedState, FinishedAuction, StartedState}
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import cqrs.UsersBid
import cqrs.commands.{GetCurrentState, PlaceBid, ScheduleAuction}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

/**
  * Created by Francois FERRARI on 21/05/2017
  */
class AuctionActorSpec3() extends TestKit(ActorSystem("AuctionSystem"))
  with ActorCommonsSpec
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "An AUCTION W/O reserve price W/1 bidder" should {

    val auction = makeAuction(
      startPrice = 0.10,
      bidIncrement = 0.10,
      startsAt = Instant.now(),
      lastsSeconds = 20,
      hasAutomaticRenewal = false,
      hasTimeExtension = false,
      sellerAUUID
    )
    val auctionActor = AuctionActor.createAuctionActor(auction)

    "schedule and start an auction" in {
      auctionActor ! ScheduleAuction(auction)
      expectMsg(AuctionScheduledReply)

      expectNoMsg(10.seconds) // Let the auction start
    }

    "accept a bid from bidderA" in {
      auctionActor ! PlaceBid(UsersBid(auction.auctionId, bidderAName, bidderAUUID, 1, 4.00, Instant.now()))
      expectMsg(BidPlacedReply)

      auctionActor ! GetCurrentState
      val expectedBidEssentials = List(
        (bidderAUUID, 1, auction.startPrice, 4.0, true, false, false)
      )
      expectMsgPF() {
        case CurrentStateReply(StartedState, ActiveAuction(activeAuction))
          if activeAuction.currentPrice == activeAuction.startPrice &&
            activeAuction.bids.length == 1 &&
            activeAuction.bids.head.bidPrice == activeAuction.startPrice &&
            activeAuction.bids.head.bidMaxPrice == 4.0 &&
            activeAuction.bids.head.bidderId == bidderAUUID &&
            bidEssentials(activeAuction.bids) == expectedBidEssentials
        => ()
      }
    }

    "be in CLOSED state when it has reached it's end time with bidderA as a winner" in {
      expectNoMsg(secondsToWaitForAuctionEnd(auction).seconds)

      auctionActor ! GetCurrentState
      expectMsgPF() {
        case CurrentStateReply(ClosedState, FinishedAuction(finishedAuction))
          if finishedAuction.bids.length == 1 &&
            finishedAuction.currentPrice == finishedAuction.startPrice &&
            finishedAuction.closedBy.isDefined &&
            finishedAuction.closedAt.isDefined &&
            finishedAuction.isSold
        => ()
      }
    }
  }
}
