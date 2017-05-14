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

  def takesBids = auctionType == AuctionType.AUCTION
  def extendIf = hasTimeExtension match {
    case true => (true, endsAt.plusSeconds(secondsToExtend))
    case false => (false, endsAt)
  }
}
