package actors

import java.time.Instant
import java.util.UUID

import actors.AuctionActor._
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import cqrs.UsersBid
import cqrs.commands.{PlaceBid, ScheduleAuction}
import models.{Auction, AuctionType, BidRejectionReason}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

/**
  * Created by Francois FERRARI on 17/05/2017
  */
class AuctionActorSpec() extends TestKit(ActorSystem("AuctionActorSpec"))
  with AuctionActorCommonsSpec
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  val scheduledAuction = Auction(
    UUID.randomUUID(),
    None, None, None,
    sellerAUUID,
    UUID.randomUUID(), UUID.randomUUID(),
    AuctionType.AUCTION,
    "Eiffel tower", "", 2010,
    UUID.randomUUID(), Nil, Nil, None,
    Nil,
    0.10, 0.10, 0.10, None,
    1, 1,
    instantNow.plusSeconds(5), None, instantNow.plusSeconds(60 * 60 * 24),
    hasAutomaticRenewal = true, hasTimeExtension = true,
    0, 0, 0,
    "EUR",
    None, Nil,
    None, None,
    instantNow
  )

  "An Auction Actor" must {
    "accept a bid" in {
      val auctionActor = AuctionActor.createAuctionActor()

      auctionActor ! ScheduleAuction(scheduledAuction)
      expectMsg(AuctionScheduledReply)
      expectNoMsg(10.seconds) // Let the auction start
      auctionActor ! PlaceBid(UsersBid(UUID.randomUUID(), bidderAName, bidderAUUID, 1, 1.00, Instant.now()))
      expectMsg(10.seconds, BidPlacedReply)
    }
  }
}
