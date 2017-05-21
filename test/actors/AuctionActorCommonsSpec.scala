package actors

import java.time.Instant
import java.util.UUID

import akka.testkit.TestKit
import models.{Auction, AuctionType, Bid}
import play.api.Logger

/**
  * Created by Francois FERRARI on 18/05/2017
  */
trait AuctionActorCommonsSpec {
  val (bidderAName, bidderAUUID) = ("bidderA", UUID.randomUUID())
  val (bidderBName, bidderBUUID) = ("bidderB", UUID.randomUUID())
  val (bidderCName, bidderCUUID) = ("bidderC", UUID.randomUUID())

  val (sellerAName, sellerAUUID) = ("sellerA", UUID.randomUUID())

  def instantNow: Instant = Instant.now()

  def secondsToWaitForAuctionEnd(auction: Auction, delay: Long = 5): Long = auction.endsAt.getEpochSecond - Instant.now().getEpochSecond match {
    case stw if stw > 0 => stw+delay
    case stx => delay
  }

  def bidEssentials(bids: Seq[Bid]): Seq[(UUID, Int, BigDecimal, BigDecimal, Boolean, Boolean, Boolean)] = bids.map(bid => (bid.bidderId, bid.requestedQty, bid.bidPrice, bid.bidMaxPrice, bid.isVisible, bid.isAuto, bid.timeExtended))

  def getScheduledAuction(startPrice: BigDecimal,
                          bidIncrement: BigDecimal,
                          startsAt: Instant,
                          lastsSeconds: Long,
                          hasAutomaticRenewal: Boolean,
                          hasTimeExtension: Boolean,
                          reservePrice: Option[BigDecimal] = None
                         ) = Auction(
    auctionId = UUID.randomUUID(),
    None, None, None,
    sellerId = sellerAUUID,
    typeId = UUID.randomUUID(), listedTimeId = UUID.randomUUID(),
    AuctionType.AUCTION,
    "Eiffel tower", "", 2010,
    areaId = UUID.randomUUID(), topicIds = Nil, options = Nil, matchedId = None,
    bids = Nil,
    startPrice = startPrice, currentPrice = startPrice, bidIncrement = bidIncrement, reservePrice = reservePrice,
    stock = 1, originalStock = 1,
    startsAt, None, startsAt.plusSeconds(lastsSeconds),
    hasAutomaticRenewal = hasAutomaticRenewal,
    hasTimeExtension = hasTimeExtension,
    renewalCount = 0, watchersCount = 0, visitorsCount = 0,
    "EUR",
    slug = None, pictures = Nil,
    closedBy = None, closedAt = None,
    isSold = false,
    createdAt = instantNow
  )
}
