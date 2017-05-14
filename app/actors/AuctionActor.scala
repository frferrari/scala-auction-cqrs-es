package actors

import java.time.Instant
import java.util.UUID

import actors.fsm.{InactiveAuction, _}
import akka.actor.Actor
import akka.persistence.fsm.PersistentFSM
import cqrs.UsersBid
import cqrs.commands.{CloseAuction, PlaceBid, StartAuction, SuspendAuction}
import cqrs.events._
import models.{Auction, Bid, BidRejectionReason}
import play.api.Logger

import scala.reflect._

/**
  * Created by Francois FERRARI on 13/05/2017
  */
class AuctionActor() extends Actor with PersistentFSM[AuctionState, AuctionStateData, AuctionEvent] {
  val msToExtend = 5000

  override def persistenceId = "auction"

  override def domainEventClassTag: ClassTag[AuctionEvent] = classTag[AuctionEvent]

  startWith(Idle, InactiveAuction)

  //
  // 	  ###   ######  #       #######
  // 	   #    #     # #       #
  // 	   #    #     # #       #
  // 	   #    #     # #       #####
  // 	   #    #     # #       #
  // 	   #    #     # #       #
  // 	  ###   ######  ####### #######
  //
  when(Idle) {
    case Event(evt: StartAuction, _) => {
      goto(Started) applying AuctionStarted(evt.auction)
    }

    case Event(evt: AuctionScheduled, _) => {
      goto(Scheduled) applying AuctionScheduled(evt.auction)
    }
  }

  //
  // 	 #####   #####  #     # ####### ######  #     # #       ####### ######
  // 	#     # #     # #     # #       #     # #     # #       #       #     #
  // 	#       #       #     # #       #     # #     # #       #       #     #
  // 	 #####  #       ####### #####   #     # #     # #       #####   #     #
  // 	      # #       #     # #       #     # #     # #       #       #     #
  // 	#     # #     # #     # #       #     # #     # #       #       #     #
  // 	 #####   #####  #     # ####### ######   #####  ####### ####### ######
  //
  when(Scheduled) {
    case Event(evt: StartAuction, _) => {
      goto(Started) applying AuctionStarted(evt.auction)
    }
    case Event(evt: CloseAuction, _) => {
      goto(Closed) applying AuctionClosed(evt.auctionId, evt.closedBy, evt.reasonId, evt.comment, evt.createdAt)
    }
    case Event(evt: SuspendAuction, _) => {
      goto(Suspended) applying AuctionSuspended(evt.auctionId, evt.suspendedBy, evt.createdAt)
    }
  }

  //
  // 	 #####  #######    #    ######  ####### ####### ######
  //	#     #    #      # #   #     #    #    #       #     #
  // 	#          #     #   #  #     #    #    #       #     #
  // 	 #####     #    #     # ######     #    #####   #     #
  // 	      #    #    ####### #   #      #    #       #     #
  // 	#     #    #    #     # #    #     #    #       #     #
  // 	 #####     #    #     # #     #    #    ####### ######
  //
  when(Started) {
    // A bid was placed on an auction not holding any bids
    case Event(PlaceBid(usersBid), ActiveAuction(auction)) if auction.takesBids && auction.bids.isEmpty => {

      val normalizedUsersBid = normalizeUsersBid(usersBid, auction)

      // A seller cannot bid on its own auctions
      if (normalizedUsersBid.bidderId == auction.sellerId) {
        stay applying BidRejected(normalizedUsersBid, BidRejectionReason.SELF_BIDDING)
      }
      // Bidding on an auction whose owner is locked in not allowed
      else if (!canReceiveBids(auction.sellerId)) {
        stay applying BidRejected(normalizedUsersBid, BidRejectionReason.SELLER_LOCKED)
      }
      // Is the bidder allowed to bid ?
      else if (!canBid(normalizedUsersBid.bidderId)) {
        stay applying BidRejected(normalizedUsersBid, BidRejectionReason.BIDDER_LOCKED)
      }
      // Bidding after the end time of an auction is not allowed
      else if (normalizedUsersBid.createdAt.isAfter(auction.endsAt)) {
        stay applying BidRejected(normalizedUsersBid, BidRejectionReason.AUCTION_HAS_ENDED)
      }
      // Bidding on an auction that has not started is not allowed
      else if (normalizedUsersBid.createdAt.isBefore(auction.startsAt)) {
        stay applying BidRejected(normalizedUsersBid, BidRejectionReason.AUCTION_NOT_YET_STARTED)
      }
      // Bidding with an erroneous qty is not allowed
      else if (normalizedUsersBid.requestedQty != 1) {
        stay applying BidRejected(normalizedUsersBid, BidRejectionReason.WRONG_REQUESTED_QTY)
      }
      // Bidding for too many auctions is not allowed
      else if (auction.stock < 1) {
        stay applying BidRejected(normalizedUsersBid, BidRejectionReason.NOT_ENOUGH_STOCK)
      }
      // Bidding below the auction's current price is not allowed
      else if (normalizedUsersBid.bidPrice < auction.currentPrice) {
        stay applying BidRejected(normalizedUsersBid, BidRejectionReason.BID_BELOW_ALLOWED_MIN)
      }
      // Validated bid
      else {
        stay applying BidPlaced(normalizedUsersBid)
      }
    }

    // A bid was placed on an auction already holding at least one bid
    case Event(PlaceBid(usersBid), ActiveAuction(auction)) if auction.takesBids && auction.bids.nonEmpty => {

      val normalizedUsersBid = normalizeUsersBid(usersBid, auction)

      // A seller cannot bid on its own auctions
      if (normalizedUsersBid.bidderId == auction.sellerId) {
        stay applying BidRejected(normalizedUsersBid, BidRejectionReason.SELF_BIDDING)
      }
      // Bidding on an auction whose owner is locked in not allowed
      else if (!canReceiveBids(auction.sellerId)) {
        stay applying BidRejected(normalizedUsersBid, BidRejectionReason.SELLER_LOCKED)
      }
      // Is the bidder allowed to bid ?
      else if (!canBid(normalizedUsersBid.bidderId)) {
        stay applying BidRejected(normalizedUsersBid, BidRejectionReason.BIDDER_LOCKED)
      }
      // Bidding after the end time of an auction is not allowed
      else if (normalizedUsersBid.createdAt.isAfter(auction.endsAt)) {
        stay applying BidRejected(normalizedUsersBid, BidRejectionReason.AUCTION_HAS_ENDED)
      }
      // Bidding on an auction that has not started is not allowed
      else if (normalizedUsersBid.createdAt.isBefore(auction.startsAt)) {
        stay applying BidRejected(normalizedUsersBid, BidRejectionReason.AUCTION_NOT_YET_STARTED)
      }
      // Bidding with an erroneous qty is not allowed
      else if (normalizedUsersBid.requestedQty != 1) {
        stay applying BidRejected(normalizedUsersBid, BidRejectionReason.WRONG_REQUESTED_QTY)
      }
      // Bidding for too many auctions is not allowed
      else if (auction.stock < 1) {
        stay applying BidRejected(normalizedUsersBid, BidRejectionReason.NOT_ENOUGH_STOCK)
      }
      // Bidding below the auction's current price is not allowed
      else if (normalizedUsersBid.bidPrice < auction.currentPrice) {
        stay applying BidRejected(normalizedUsersBid, BidRejectionReason.BID_BELOW_ALLOWED_MIN)
      }
      // Validated bid
      else {
        stay applying BidPlaced(normalizedUsersBid)
      }
    }

    // A bid was placed on a fixed price auction
    case Event(PlaceBid(usersBid), ActiveAuction(auction)) if ! auction.takesBids => {

      val normalizedUsersBid = normalizeUsersBid(usersBid, auction)

      // A seller cannot bid on its own auctions
      if (normalizedUsersBid.bidderId == auction.sellerId) {
        stay applying BidRejected(normalizedUsersBid, BidRejectionReason.SELF_BIDDING)
      }
      // Bidding on an auction whose owner is locked in not allowed
      else if (!canReceiveBids(auction.sellerId)) {
        stay applying BidRejected(normalizedUsersBid, BidRejectionReason.SELLER_LOCKED)
      }
      // Is the bidder allowed to bid ?
      else if (!canBid(normalizedUsersBid.bidderId)) {
        stay applying BidRejected(normalizedUsersBid, BidRejectionReason.BIDDER_LOCKED)
      }
      // Bidding after the end time of an auction is not allowed
      else if (normalizedUsersBid.createdAt.isAfter(auction.endsAt)) {
        stay applying BidRejected(normalizedUsersBid, BidRejectionReason.AUCTION_HAS_ENDED)
      }
      // Bidding on an auction that has not started is not allowed
      else if (normalizedUsersBid.createdAt.isBefore(auction.startsAt)) {
        stay applying BidRejected(normalizedUsersBid, BidRejectionReason.AUCTION_NOT_YET_STARTED)
      }
      // Bidding with an erroneous qty is not allowed
      else if (normalizedUsersBid.requestedQty != 1) {
        stay applying BidRejected(normalizedUsersBid, BidRejectionReason.WRONG_REQUESTED_QTY)
      }
      // Bidding for too many auctions is not allowed
      else if (auction.stock < 1) {
        stay applying BidRejected(normalizedUsersBid, BidRejectionReason.NOT_ENOUGH_STOCK)
      }
      // Bidding below the auction's current price is not allowed
      else if (normalizedUsersBid.bidPrice != auction.currentPrice) {
        stay applying BidRejected(normalizedUsersBid, BidRejectionReason.WRONG_BID_PRICE)
      }
      // Validated bid
      else {
        stay applying BidPlaced(normalizedUsersBid)
      }
    }
  }

  // override def applyEvent(event: AuctionEvent, stateDataBefore: AuctionStateData): AuctionStateData = event match {
  override def applyEvent(event: AuctionEvent, stateDataBefore: AuctionStateData): AuctionStateData = (event, stateDataBefore) match {
    case (AuctionStarted(auction), InactiveAuction) => stateDataBefore.startAuction(auction)

    case (AuctionScheduled(auction), InactiveAuction) => stateDataBefore.scheduleAuction(auction)

    case (AuctionClosed(auctionId, closedBy, reasonId, comment, createdAt), ActiveAuction(auction)) => stateDataBefore.closeAuction(closedBy, createdAt)

    /**
      * A bid was placed on an auction not holding any bids
      */
    case (BidPlaced(usersBid), ActiveAuction(auction)) if auction.takesBids && auction.bids.isEmpty => {
      val (isTimeExtended, endsAt) = auction.extendIf
      /**
        * If the auction has a reserve price and the users bid price is >= reserve_price then the current_price is raised
        * to reach the value of the auction's reserve price. This allows a bidder who would be the sole bidder to win the auction.
        */
      val currentPrice = auction.reservePrice match {
        case Some(reservePrice) if usersBid.bidPrice >= reservePrice => reservePrice
        case _ => auction.currentPrice
      }
      val bid = Bid(usersBid, true, false, isTimeExtended, currentPrice)

      stateDataBefore.placeBids(List(bid), endsAt, currentPrice)
    }

    /**
      * A bid was placed on an auction already holding at least one bid
      */
    case (BidPlaced(usersBid), ActiveAuction(auction)) if auction.takesBids && auction.bids.nonEmpty => {
      val highestBid: Bid = auction.bids.head
      val (isTimeExtended, endsAt) = auction.extendIf

      if (usersBid.bidderId == highestBid.bidderId && auction.reservePrice.isEmpty) {
        /**
          * The current highest bidder wants to raise its max bid price.
          * The auction's current price doesn't change, and the new bid isn't visible
          */
        val currentPrice = auction.currentPrice
        val bid = Bid(usersBid, false, false, isTimeExtended, currentPrice)

        stateDataBefore.placeBids(List(bid), endsAt, currentPrice)
      }
      else if (usersBid.bidderId == highestBid.bidderId && auction.reservePrice.isDefined) {
        /**
          * The current highest bidder wants to raise its max bid price.
          *
          * If the users bid price is >= auction's reserve price and it's the first time we exceed the reserve price
          * then the auction's current price is raised to reach the value of the auction's reserve price
          * and the new bid is visible.
          */
        val (isBidVisible, currentPrice) = if (usersBid.bidPrice >= auction.reservePrice.get) {
          if (auction.currentPrice < auction.reservePrice.get)
            (true, auction.reservePrice.get)
          else
            (false, auction.currentPrice)
        }
        else
          (false, auction.currentPrice)

        val bid = Bid(usersBid, isBidVisible, false, isTimeExtended, currentPrice)

        stateDataBefore.placeBids(List(bid), endsAt, currentPrice)
      }
      else if (usersBid.bidPrice <= highestBid.bidMaxPrice) {
        /**
          * Case of a bid that is greater than the auction's current price AND lower than the highest bidder max bid.
          *
          * The highest bidder keeps its position of highest bidder, and we raise the auction's current price to the
          * user's bid price
          */
        val currentPrice = usersBid.bidPrice
        val bid = Bid(usersBid, true, false, isTimeExtended, currentPrice)
        val updatedHighestBid = highestBid.copy(isVisible = true, isAuto = true, timeExtended = isTimeExtended, bidPrice = currentPrice)

        stateDataBefore.placeBids(List(updatedHighestBid, bid), endsAt, currentPrice)
      }
      else {
        /**
          * Case when the user's bid price is greater than the highest bid max value.
          * The current highest_bidder loses its status of highest bidder.
          *
          * If the auction has a reserve price and the user's bid price is >= reserve price then the
          * auction's current price is raised to reach the value of the auction's reserve price.
          */
        val currentPrice = auction.reservePrice match {
          case Some(reservePrice) if usersBid.bidPrice >= reservePrice => reservePrice
          case _ => highestBid.bidMaxPrice + auction.bidIncrement
        }

        val newHighestBid = Bid(usersBid, true, false, isTimeExtended, currentPrice)

        /**
          * We don't generate an automatic bid for the current highest bidder if the auction's price has already reached
          * the highest bidder max bid value
          */
        if (auction.currentPrice == highestBid.bidMaxPrice) {
          stateDataBefore.placeBids(List(newHighestBid), endsAt, currentPrice)
        }
        else {
          val newBid = highestBid.copy(isVisible = true, isAuto = true, timeExtended = isTimeExtended, bidPrice = highestBid.bidMaxPrice)
          stateDataBefore.placeBids(List(newHighestBid, newBid), endsAt, currentPrice)
        }
      }
    }

    /**
      * A bid was placed on a fixed price auction
      */
    case (BidPlaced(usersBid), ActiveAuction(auction)) if ! auction.takesBids => {
      val (isTimeExtended, endsAt) = auction.extendIf
      val currentPrice = auction.currentPrice

      auction.stock - usersBid.requestedQty match {
        case stock if stock == 0 =>
          Logger.info(s"Auction {auction.auctionId} sold for a qty of #{usersBid.requestedQty}, no remaining stock")
          val bid = Bid(usersBid, true, false, isTimeExtended, usersBid.bidPrice)
          /* TODO
          %FsmAuctionData{fsm_data | 	closed_by: nil,
																			original_stock: event.requested_qty,
																			stock: 0,
																			end_date_time: event.created_at,
                                      bids: [Map.from_struct(new_bid)]}
           */
          stateDataBefore.placeBids(List(bid), endsAt, currentPrice)

        case _ =>
          Logger.info(s"Auction {auction.auctionId} sold for a qty of #{usersBid.requestedQty}, remaining stock is #{stock}, duplicate the auction")
          val bid = Bid(usersBid, true, false, isTimeExtended, usersBid.bidPrice)
          /* TODO Handle duplication
            %FsmAuctionData{fsm_data | 	closed_by: nil,
																			  original_stock: event.requested_qty,
																			  stock: 0,
																			  clone_parameters: %{stock: new_stock,
																													start_date_time: fsm_data.start_date_time,
																													end_date_time: fsm_data.end_date_time},
																			  end_date_time: event.created_at,
																			  bids: [Map.from_struct(new_bid)]}
           */
          stateDataBefore.placeBids(List(bid), endsAt, currentPrice)
      }
    }
  }

  /**
    * Aligns the users's bid price to the auction's bidIncrement boundaries
    *
    * @param usersBid
    * @param auction
    * @return
    */
  def normalizeUsersBid(usersBid: UsersBid, auction: Auction) = {
    usersBid.copy(
      bidPrice = boundedBidPrice(usersBid.bidPrice, auction.bidIncrement)
    )
  }

  // TODO Align to bidIncrement
  def boundedBidPrice(bidPrice: BigDecimal, bidIncrement: BigDecimal) = bidPrice

  def updateCurrentPriceAndBids(stateData: ActiveAuction, newCurrentPrice: BigDecimal, newBids: Seq[Bid]) = {
    stateData.auction.copy(currentPrice = newCurrentPrice, bids = newBids ++ stateData.auction.bids)
  }

  // TODO implement by calling the user's actor
  def canReceiveBids(sellerId: UUID) = true

  // TODO implement
  def canBid(bidderId: UUID) = true // TODO implement
}
