package actors

import actors.auction.AuctionActor
import actors.auction.AuctionActor._
import actors.auction.fsm.{ClosedState, FinishedAuction}
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import cqrs.commands.{GetCurrentState, ScheduleAuction}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

/**
  * Created by Francois FERRARI on 21/05/2017
  */
class AuctionActorSpec1() extends TestKit(ActorSystem("AuctionActorSpec"))
  with AuctionActorCommonsSpec
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "An AUCTION W/O reserve price W/O automatic renewal W/O bidders" should {

    val auctionActor = AuctionActor.createAuctionActor(Some("test1"))
    val auction = getScheduledAuction(startPrice = 0.10, bidIncrement = 0.10, lastsSeconds = 20)

    "start an auction in ScheduledState" in {
      auctionActor ! ScheduleAuction(auction)
      expectMsg(AuctionScheduledReply)
    }

    "be in CLOSED state when it has reached it's end time" in {
      expectNoMsg(secondsToWaitForAuctionEnd(auction).seconds)

      auctionActor ! GetCurrentState
      expectMsgPF() {
        case CurrentStateReply(ClosedState, FinishedAuction(auction))
          if auction.bids.length == 0 &&
            auction.currentPrice == auction.startPrice &&
            auction.closedBy.isDefined &&
            auction.closedAt.isDefined
        => ()
      }
    }
  }
}
