package models

import java.time.Instant
import java.util.UUID

import models.AuctionType.AuctionType

/**
  * Created by Francois FERRARI on 13/05/2017
  */
object AuctionType extends Enumeration {
  type AuctionType = Value
  val AUCTION, FIXED_PRICE = Value
}

case class Auction(auctionId: Option[UUID],
                   clonedFromAuctionId: Option[UUID],
                   clonedToAuctionId: Option[UUID],
                   sellerId: UUID,
                   typeId: UUID,
                   listedTimeId: UUID,
                   auctionType: AuctionType,
                   title: String,
                   description: String,
                   year: Int,
                   areaId: UUID,
                   topicIds: Seq[UUID],
                   options: Seq[UUID],
                   matchedId: Option[UUID],
                   bids: Seq[Bid],
                   startPrice: BigDecimal,
                   currentPrice: BigDecimal,
                   bidIncrement: BigDecimal,
                   reservePrice: Option[BigDecimal],
                   stock: Int,
                   startsAt: Instant,
                   endsAt: Instant,
                   hasAutomaticRenewal: Boolean,
                   hasTimeExtension: Boolean,
                   renewalCount: Int,
                   watchersCount: Int, // this count doesn't exist in the legacy model
                   visitorsCount: Int, // named "view_count" in the legacy model
                   currency: String,
                   slug: Option[String],
                   pictures: Seq[UUID],
                   closedBy: Option[UUID],
                   closedAt: Option[Instant],
                   createdAt: Instant
                  ) {

  val secondsToExtend = 5000

  /**
    *
    * @return
    */
  def takesBids = auctionType == AuctionType.AUCTION

  /**
    * Computes the new end time of an auction
    * @return
    */
  def extendIf = hasTimeExtension match {
    case true => (true, endsAt.plusSeconds(secondsToExtend))
    case false => (false, endsAt)
  }

  /**
    * Aligns a bid price to a bid increment boundary
    *
    *	Ex: bidPrice=1.14 bidIncrement=0.10 -> bidPrice=1.10
    * Ex: bidPrice=1.19 bidIncrement=0.10 -> bidPrice=1.10
    * Ex: bidPrice=1.00 bidIncrement=0.10 -> bidPrice=1.00
    *
    * @param bidPrice
    * @param bidIncrement
    * @return
    */
  def boundedBidPrice(bidPrice: BigDecimal, bidIncrement: BigDecimal) = {
    BigDecimal((bidPrice / bidIncrement).toInt)*bidIncrement
  }
}
