package actors

import java.time.Instant

import actors.auction.AuctionActor
import actors.auction.AuctionActor._
import actors.auction.fsm.{ActiveAuction, StartedState}
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import cqrs.commands.{GetCurrentState, ScheduleAuction}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

/**
  * Created by Francois FERRARI on 21/05/2017
  */
class AuctionActorSpec2() extends TestKit(ActorSystem("AuctionActorSpec"))
  with ActorCommonsSpec
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "An AUCTION W/O reserve price W/automatic renewal W/O bidders" should {

    val auction = makeAuction(
      startPrice = 0.10,
      bidIncrement = 0.10,
      startsAt = Instant.now(),
      lastsSeconds = 10,
      hasAutomaticRenewal = true,
      hasTimeExtension = false,
      sellerAUUID
    )
    val auctionActor = AuctionActor.createAuctionActor(auction)

    "start an auction in ScheduledState" in {
      auctionActor ! ScheduleAuction(auction)
      expectMsg(AuctionScheduledReply)
    }

    "be RESTARTED when it has reached it's end time" in {
      expectNoMsg(secondsToWaitForAuctionEnd(auction).seconds)

      auctionActor ! GetCurrentState
      expectMsgPF() {
        case CurrentStateReply(StartedState, ActiveAuction(restartedAuction))
          if restartedAuction.bids.isEmpty &&
            restartedAuction.currentPrice == restartedAuction.startPrice &&
            restartedAuction.closedBy.isEmpty &&
            restartedAuction.closedAt.isEmpty &&
            restartedAuction.startsAt == auction.endsAt &&
            restartedAuction.endsAt.isAfter(restartedAuction.startsAt) &&
            ! restartedAuction.isSold
        => ()
      }
    }
  }
}
