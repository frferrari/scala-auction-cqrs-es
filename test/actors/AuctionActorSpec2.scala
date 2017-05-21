package actors

import java.time.Instant

import actors.auction.AuctionActor
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
class AuctionActorSpec2() extends TestKit(ActorSystem("AuctionActorSpec"))
  with AuctionActorCommonsSpec
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "An AUCTION W/O reserve price W/1 bidder" should {

    val auctionActor = AuctionActor.createAuctionActor()
    val auction = getScheduledAuction(startPrice = 0.10, bidIncrement = 0.10, lastsSeconds = 20)

    "schedule and start an auction" in {
      auctionActor ! ScheduleAuction(auction)
      expectMsg(AuctionScheduledReply)

      expectNoMsg(10.seconds) // Let the auction start
    }

    "accept a first bid from bidderA" in {
      auctionActor ! PlaceBid(UsersBid(auction.auctionId, bidderAName, bidderAUUID, 1, 4.00, Instant.now()))
      expectMsg(BidPlacedReply)

      auctionActor ! GetCurrentState
      val expectedBidEssentials = List(
        (bidderAUUID, 1, auction.startPrice, 4.0, true, false, false)
      )
      expectMsgPF() {
        case CurrentStateReply(StartedState, ActiveAuction(auction))
          if auction.currentPrice == auction.startPrice &&
            auction.bids.length == 1 &&
            auction.bids.head.bidPrice == auction.startPrice &&
            auction.bids.head.bidMaxPrice == 4.0 &&
            auction.bids.head.bidderId == bidderAUUID &&
            bidEssentials(auction.bids) == expectedBidEssentials
        => ()
      }
    }

    "be in CLOSED state when it has reached it's end time with a winner" in {
      expectNoMsg(secondsToWaitForAuctionEnd(auction).seconds)

      auctionActor ! GetCurrentState
      expectMsgPF() {
        case CurrentStateReply(ClosedState, FinishedAuction(auction))
          if auction.bids.length == 1 &&
            auction.currentPrice == auction.startPrice &&
            auction.closedBy.isDefined &&
            auction.closedAt.isDefined
        => ()
      }
    }
  }
}
