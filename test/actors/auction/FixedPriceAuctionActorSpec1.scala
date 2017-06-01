package actors.auction

import java.time.Instant

import actors.ActorCommonsSpec
import actors.auction.AuctionActor._
import actors.auction.fsm.{ClosedState, FinishedAuction}
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import cqrs.commands.{GetCurrentState, ScheduleAuction}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

/**
  * Created by Francois FERRARI on 17/05/2017
  */
class FixedPriceAuctionActorSpec1() extends TestKit(ActorSystem("AuctionActorSpec"))
  with ActorCommonsSpec
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "A scheduled FIXED PRICE auction" should {

    val auction = makeFixedPriceAuction(0.10, Instant.now().plusSeconds(2), 5, 10, hasAutomaticRenewal = false, sellerAUUID)
    val auctionActor = AuctionActor.createAuctionActor(auction)
    auctionActor ! ScheduleAuction(auction)

    "be in CLOSED state and NOT SOLD" in {
      expectMsg(AuctionScheduledReply)

      val ttw = auction.endsAt.getEpochSecond - Instant.now().getEpochSecond + 1
      expectNoMsg(ttw.seconds)

      auctionActor ! GetCurrentState
      expectMsgPF() {
        case CurrentStateReply(ClosedState, FinishedAuction(finishedAuction))
          if finishedAuction.bids.isEmpty &&
            !finishedAuction.isSold
        => ()
      }
    }
  }
}
