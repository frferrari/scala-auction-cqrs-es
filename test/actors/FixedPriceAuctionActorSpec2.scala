package actors

import java.time.Instant

import actors.auction.AuctionActor
import actors.auction.AuctionActor._
import actors.auction.fsm.{ActiveAuction, ClosedState, FinishedAuction, StartedState}
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import cqrs.commands.{GetCurrentState, ScheduleAuction}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

/**
  * Created by Francois FERRARI on 17/05/2017
  */
class FixedPriceAuctionActorSpec2() extends TestKit(ActorSystem("AuctionActorSpec"))
  with AuctionActorCommonsSpec
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "A scheduled FIXED PRICE auction" should {

    val auctionActor = AuctionActor.createAuctionActor()
    val auction = getScheduledFixedPriceAuction(0.10, Instant.now().plusSeconds(2), 5, 10, hasAutomaticRenewal = true)
    auctionActor ! ScheduleAuction(auction)

    "be RESTARTED and NOT SOLD" in {
      expectMsg(AuctionScheduledReply)

      val ttw = auction.endsAt.getEpochSecond - Instant.now().getEpochSecond + 1
      expectNoMsg(ttw.seconds)

      auctionActor ! GetCurrentState
      expectMsgPF() {
        case CurrentStateReply(StartedState, ActiveAuction(activeAuction))
          if activeAuction.bids.isEmpty &&
            activeAuction.startsAt == auction.endsAt &&
            activeAuction.endsAt.isAfter(auction.endsAt) &&
            !activeAuction.isSold
        => ()
      }
    }
  }
}
