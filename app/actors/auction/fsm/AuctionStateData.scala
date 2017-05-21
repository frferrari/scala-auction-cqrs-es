package actors.auction.fsm

import java.time.Instant
import java.util.UUID

import cqrs.events.{AuctionClosed, AuctionRestarted}
import models.{Auction, Bid}
import play.api.Logger

/**
  * Created by Francois FERRARI on 13/05/2017
  */
sealed trait AuctionStateData {
  def startAuction(auction: Auction): AuctionStateData

  def scheduleAuction(auction: Auction): AuctionStateData

  def closeAuction(auctionClosed: AuctionClosed): AuctionStateData

  def placeBids(bids: Seq[Bid], isSold: Boolean, updatedEndsAt: Instant, updatedCurrentPrice: BigDecimal, updatedStock: Int, updatedOriginalStock: Int, updatedClosedBy: Option[UUID] = None): AuctionStateData

  def restartAuction(auction: Auction): AuctionStateData
}

case object InactiveAuction extends AuctionStateData {
  def startAuction(auction: Auction): AuctionStateData = ActiveAuction(auction)

  def scheduleAuction(auction: Auction) = ActiveAuction(auction)

  def closeAuction(auctionClosed: AuctionClosed) = this

  def placeBids(bids: Seq[Bid], isSold: Boolean, updatedEndsAt: Instant, updatedCurrentPrice: BigDecimal, updatedStock: Int, updatedOriginalStock: Int, updatedClosedBy: Option[UUID] = None) = this

  def restartAuction(auction: Auction) = this
}

case class ActiveAuction(auction: Auction) extends AuctionStateData {
  def startAuction(auction: Auction) = this

  def scheduleAuction(auction: Auction) = ActiveAuction(auction)

  def closeAuction(ac: AuctionClosed) = {
    val isSold = auction match {
      case a if a.bids.isEmpty => false // No Bid(s) -> no winner
      case a if a.reservePrice.isEmpty => true // Bid(s), no reserve price -> a winner
      case a if a.currentPrice >= a.reservePrice.get => true // Bid(s), a current price >= reserve price -> a winner
      case _ => false // Bid(s), a current price < reserve price -> no winner
    }

    val updatedAuction = auction.copy(
      closedBy = Some(ac.closedBy),
      closedAt = Some(ac.createdAt),
      originalStock = auction.stock,
      stock = 0,
      renewalCount = auction.renewalCount + 1,
      cloneParameters = ac.cloneParameters,
      isSold = isSold
    )

    FinishedAuction(updatedAuction)
  }

  def placeBids(bids: Seq[Bid], isSold: Boolean, updatedEndsAt: Instant, updatedCurrentPrice: BigDecimal, updatedStock: Int, updatedOriginalStock: Int, updatedClosedBy: Option[UUID] = None) = {
    val updatedAuction = auction.copy(
      bids = bids ++ auction.bids,
      endsAt = updatedEndsAt,
      currentPrice = updatedCurrentPrice,
      stock = updatedStock,
      originalStock = updatedOriginalStock,
      closedBy = updatedClosedBy,
      isSold = isSold
    )

    if (updatedStock == 0)
      FinishedAuction(updatedAuction)
    else
      ActiveAuction(updatedAuction)
  }

  def restartAuction(auction: Auction) = ActiveAuction(
    auction.copy(
      startsAt = auction.endsAt,
      endsAt = auction.endsAt.plusSeconds(auction.endsAt.getEpochSecond - auction.startsAt.getEpochSecond)
    )
  )
}

case class FinishedAuction(auction: Auction) extends AuctionStateData {
  def startAuction(auction: Auction) = this

  def scheduleAuction(auction: Auction) = this

  def closeAuction(auctionClosed: AuctionClosed) = this

  def placeBids(bids: Seq[Bid], isSold: Boolean, updatedEndsAt: Instant, updatedCurrentPrice: BigDecimal, updatedStock: Int, updatedOriginalStock: Int, updatedClosedBy: Option[UUID] = None) = this

  def restartAuction(auction: Auction) = this
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
