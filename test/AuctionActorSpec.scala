import java.time.Instant
import java.util.UUID

import actors.AuctionActor
import actors.AuctionActor.{AuctionClosedReply, AuctionScheduledReply, AuctionStartedReply, BidPlacedReply}
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import cqrs.UsersBid
import cqrs.commands.{PlaceBid, ScheduleAuction}
import models.{Auction, AuctionType}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

/**
  * Created by Francois FERRARI on 17/05/2017
  */
class AuctionActorSpec() extends TestKit(ActorSystem("AuctionActorSpec"))
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  val (bidderAName, bidderAUUID) = ("francois", UUID.randomUUID())
  val (sellerAName, sellerAUUID) = ("emmanuel", UUID.randomUUID())

  val instantNow = Instant.now()

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

  val scheduledFixedPriceAuction = Auction(
    auctionId = UUID.randomUUID(),
    clonedFromAuctionId = None,
    clonedToAuctionId = None,
    cloneParameters = None,
    sellerId = sellerAUUID,
    typeId = UUID.randomUUID(),
    listedTimeId = UUID.randomUUID(),
    AuctionType.FIXED_PRICE,
    title = "Eiffel tower",
    description = "",
    year = 2010,
    areaId = UUID.randomUUID(),
    topicIds = Nil,
    options = Nil,
    matchedId = None,
    bids = Nil,
    startPrice = 0.10,
    currentPrice = 0.10,
    bidIncrement = 0.10,
    reservePrice = None,
    stock = 1,
    originalStock = 1,
    startsAt = instantNow.plusSeconds(5),
    suspendedAt = None,
    endsAt = instantNow.plusSeconds(60 * 60 * 24),
    hasAutomaticRenewal = true,
    hasTimeExtension = true,
    renewalCount = 0,
    watchersCount = 0,
    visitorsCount = 0,
    currency = "EUR",
    slug = None,
    pictures = Nil,
    closedBy = None,
    closedAt = None,
    instantNow
  )

  "An Auction Actor" must {
    "accept PlaceBid " in {
      val auctionActor = AuctionActor.createAuctionActor()

      auctionActor ! ScheduleAuction(scheduledAuction)
      expectNoMsg(10.seconds)
      auctionActor ! PlaceBid(UsersBid(UUID.randomUUID(), bidderAName, bidderAUUID, 1, 1.00, Instant.now()))
      expectMsg(BidPlacedReply)
    }
  }

  "A Fixed Price Auction Actor" should {
    "be closed after receiving a bid for the exact available stock" in {
      val auctionActor = AuctionActor.createAuctionActor()

      auctionActor ! ScheduleAuction(scheduledFixedPriceAuction)
      expectNoMsg(10.seconds)
      auctionActor ! PlaceBid(UsersBid(UUID.randomUUID(), bidderAName, bidderAUUID, scheduledFixedPriceAuction.stock, scheduledFixedPriceAuction.currentPrice, Instant.now()))
      expectNoMsg(10.seconds)
    }
  }
}


