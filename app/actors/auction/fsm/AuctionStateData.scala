package actors.auction.fsm

import java.time.Instant
import java.util.UUID

import cqrs.events.AuctionClosed
import models.{Auction, Bid}
import play.api.Logger

/**
  * Created by Francois FERRARI on 13/05/2017
  */
sealed trait AuctionStateData {
  def startAuction(auction: Auction): AuctionStateData

  def scheduleAuction(auction: Auction): AuctionStateData

  def closeAuction(auctionClosed: AuctionClosed): AuctionStateData

  def placeBids(bids: Seq[Bid], updatedEndsAt: Instant, updatedCurrentPrice: BigDecimal, updatedStock: Int, updatedOriginalStock: Int, updatedClosedBy: Option[UUID] = None): AuctionStateData
}

case object InactiveAuction extends AuctionStateData {
  def startAuction(auction: Auction): AuctionStateData = ActiveAuction(auction)

  def scheduleAuction(auction: Auction) = ActiveAuction(auction)

  def closeAuction(auctionClosed: AuctionClosed) = this

  def placeBids(bids: Seq[Bid], updatedEndsAt: Instant, updatedCurrentPrice: BigDecimal, updatedStock: Int, updatedOriginalStock: Int, updatedClosedBy: Option[UUID] = None) = this
}

case class ActiveAuction(auction: Auction) extends AuctionStateData {
  def startAuction(auction: Auction) = this

  def scheduleAuction(auction: Auction) = ActiveAuction(auction)

  def closeAuction(ac: AuctionClosed) = {
    val updatedAuction = auction.copy(
      closedBy = Some(ac.closedBy),
      closedAt = Some(ac.createdAt),
      originalStock = auction.stock,
      stock = 0,
      renewalCount = auction.renewalCount + 1,
      cloneParameters = ac.cloneParameters
    )

    FinishedAuction(updatedAuction)
  }

  def placeBids(bids: Seq[Bid], updatedEndsAt: Instant, updatedCurrentPrice: BigDecimal, updatedStock: Int, updatedOriginalStock: Int, updatedClosedBy: Option[UUID] = None) = {
    val updatedAuction = auction.copy(
      bids = bids ++ auction.bids,
      endsAt = updatedEndsAt,
      currentPrice = updatedCurrentPrice,
      stock = updatedStock,
      originalStock = updatedOriginalStock,
      closedBy = updatedClosedBy
    )

    if (updatedStock == 0)
      FinishedAuction(updatedAuction)
    else
      ActiveAuction(updatedAuction)
  }
}

case class FinishedAuction(auction: Auction) extends AuctionStateData {
  def startAuction(auction: Auction) = this

  def scheduleAuction(auction: Auction) = this

  def closeAuction(auctionClosed: AuctionClosed) = this

  def placeBids(bids: Seq[Bid], updatedEndsAt: Instant, updatedCurrentPrice: BigDecimal, updatedStock: Int, updatedOriginalStock: Int, updatedClosedBy: Option[UUID] = None) = this
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
