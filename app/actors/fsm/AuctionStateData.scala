package actors.fsm

import java.time.Instant
import java.util.UUID

import models.{Auction, Bid}

/**
  * Created by Francois FERRARI on 13/05/2017
  */
sealed trait AuctionStateData {
  def startAuction(auction: Auction): AuctionStateData
  def scheduleAuction(auction: Auction): AuctionStateData
  def closeAuction(closedBy: UUID, createdAt: Instant): AuctionStateData
  def placeBids(bids: Seq[Bid], updatedEndsAt: Instant, updatedCurrentPrice: BigDecimal): AuctionStateData
}

case object InactiveAuction extends AuctionStateData {
  def startAuction(auction: Auction): AuctionStateData = ActiveAuction(auction)
  def scheduleAuction(auction: Auction) = ActiveAuction(auction)
  def closeAuction(closedBy: UUID, createdAt: Instant) = this
  def placeBids(bids: Seq[Bid], updatedEndsAt: Instant, updatedCurrentPrice: BigDecimal) = this
}

case class ActiveAuction(auction: Auction) extends AuctionStateData {
  def startAuction(auction: Auction) = this
  def scheduleAuction(auction: Auction) = ActiveAuction(auction)
  def closeAuction(closedBy: UUID, createdAt: Instant) = TerminatedAuction(auction.copy(closedBy = Some(closedBy), closedAt = Some(createdAt)))
  def placeBids(bids: Seq[Bid], updatedEndsAt: Instant, updatedCurrentPrice: BigDecimal) =
    ActiveAuction(auction.copy(bids = bids ++ auction.bids, endsAt = updatedEndsAt, currentPrice = updatedCurrentPrice))
}

case class TerminatedAuction(auction: Auction) extends AuctionStateData {
  def startAuction(auction: Auction) = this
  def scheduleAuction(auction: Auction) = this
  def closeAuction(closedBy: UUID, createdAt: Instant) = this
  def placeBids(bids: Seq[Bid], updatedEndsAt: Instant, updatedCurrentPrice: BigDecimal) = this
}

//case class AuctionStateData(auctionId: UUID,
//                            clonedToAuctionId: Option[UUID],
//                            clonedFromAuctionId: Option[UUID],
//                            sellerId: UUID,
//                            typeId: UUID,
//                            listedTimeId: UUID,
//                            saleTypeId: UUID,
//                            title: String,
//                            description: String,
//                            year: Int,
//                            areaId: UUID,
//                            topic_ids: Seq[UUID],
//                            options: Seq[UUID],
//                            matchedId: Option[UUID],
//                            startPrice: BigDecimal,
//                            currentPrice: BigDecimal,
//                            reservePrice: Option[BigDecimal],
//                            bidIncrement: BigDecimal,
//                            currency: String,
//                            originalStock: Int,
//                            stock: Int,
//                            cloneParameters: Seq[String], // TODO Check this
//                            startsAt: Instant,
//                            endsAt: Instant,
//                            createdAt: Instant,
//                            hasAutomaticRenewal: Boolean,
//                            hasTimeExtension: Boolean,
//                            renewalCount: Int,
//                            watchersCount: Int, // this count doesn't exist in the legacy model
//                            visitorsCount: Int, // named "view_count" in the legacy model
//                            currency: String,
//                            slug: Option[String],
//                            pictures: Seq[UUID],
//                            createdAt: Instant,
//                            //
//                            // closed_by values are
//                            //
//                            // nil -> not closed
//                            // @closed_by_system -> closed by the system
//                            // is_integer(user_id) -> closed by the user_id
//                            //
//                            closedBy: UUID,
//                            isSold: Boolean,
//                            suspendedAt: Boolean,
//                            slug: String,
//                            pictures: Seq[UUID],
//                            bids: Seq[String], // TODO Create the Bid class
//                            // ticker_ref: ... // TODO Store the ticker handler here
//                           )
