package actors.auction

import java.time.Instant
import java.util.UUID

import actors.auction.AuctionActor._
import actors.auction.fsm._
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.persistence.fsm.PersistentFSM
import cqrs.UsersBid
import cqrs.commands._
import cqrs.events._
import models.AuctionReason.AuctionReason
import models.BidRejectionReason.BidRejectionReason
import models._
import play.api.Logger

import scala.concurrent.duration._
import scala.reflect._

/**
  * Created by Francois FERRARI on 13/05/2017
  */

object Test {
  def main(args: Array[String]): Unit = {
    Logger.info("Main is starting")

    implicit val system = ActorSystem("andycotSystem")
    val auctionActorRef: ActorRef = AuctionActor.createAuctionActor()

    val (bidderAName, bidderAUUID) = ("francois", UUID.randomUUID())
    val (sellerAName, sellerAUUID) = ("emmanuel", UUID.randomUUID())

    val instantNow = Instant.now()

    val auction = Auction(
      UUID.randomUUID(),
      None, None, None,
      sellerAUUID,
      UUID.randomUUID(), UUID.randomUUID(), AuctionType.AUCTION,
      "Eiffel tower", "", 2010,
      UUID.randomUUID(), Nil, Nil, None,
      Nil,
      0.10, 0.10, 0.10, None,
      1, 1,
      // instantNow.plusSeconds(5), None, instantNow.plusSeconds(60 * 60 * 24),
      instantNow, None, instantNow.plusSeconds(60 * 60 * 24),
      hasAutomaticRenewal = true, hasTimeExtension = true,
      0, 0, 0,
      "EUR",
      None, Nil,
      None, None,
      instantNow
    )

    // auctionActor ! ScheduleAuction(auction)
    auctionActorRef ! StartAuction(auction)
    // auctionActor ! CloseAuction(auction.auctionId.get, UUID.randomUUID(), UUID.randomUUID(), "Closed manually", Instant.now())
    auctionActorRef ! PlaceBid(UsersBid(UUID.randomUUID(), bidderAName, bidderAUUID, 1, 1.00, Instant.now()))

    Thread.sleep(60000)

    system.terminate()
  }
}

class AuctionActor() extends Actor with PersistentFSM[AuctionState, AuctionStateData, AuctionEvent] {
  val msToExtend = 5000

  override def persistenceId: String = self.path.name

  override def domainEventClassTag: ClassTag[AuctionEvent] = classTag[AuctionEvent]

  startWith(fsm.IdleState, InactiveAuction)

  //
  // 	  ###   ######  #       #######
  // 	   #    #     # #       #
  // 	   #    #     # #       #
  // 	   #    #     # #       #####
  // 	   #    #     # #       #
  // 	   #    #     # #       #
  // 	  ###   ######  ####### #######
  //
  when(fsm.IdleState) {
    case Event(evt: StartAuction, _) =>
      goto(StartedState) applying AuctionStarted(evt.auction) replying AuctionStartedReply

    case Event(evt: ScheduleAuction, _) =>
      goto(ScheduledState) applying AuctionScheduled(evt.auction) replying AuctionScheduledReply andThen {
        case ActiveAuction(auction) => startScheduleTimer(auction)
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
  when(ScheduledState) {
    case Event(evt: StartAuction, _) =>
      goto(StartedState) applying AuctionStarted(evt.auction) replying AuctionStartedReply andThen {
        case ActiveAuction(auction) => startCloseTimer(auction)
      }

    case Event(evt: StartAuctionByTimer, _) =>
      goto(StartedState) applying AuctionStarted(evt.auction) andThen {
        case ActiveAuction(auction) => startCloseTimer(auction)
      }

    case Event(PlaceBid(usersBid), _) =>
      stay replying BidRejectedReply(usersBid, BidRejectionReason.AUCTION_NOT_YET_STARTED)

    case Event(evt: CloseAuction, _) =>
      goto(ClosedState) applying AuctionClosed(evt) replying AuctionClosedReply(evt.reason)

    case Event(evt: SuspendAuction, _) =>
      goto(SuspendedState) applying AuctionSuspended(evt.auctionId, evt.suspendedBy, evt.createdAt) replying AuctionSuspendedReply(evt.reason)
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
  when(StartedState) {
    // A bid was placed on an auction without bids
    case Event(PlaceBid(usersBid), ActiveAuction(auction)) if auction.takesBids && auction.bids.isEmpty =>

      val normalizedUsersBid = normalizeUsersBid(usersBid, auction)

      // A seller cannot bid on its own auctions
      if (normalizedUsersBid.bidderId == auction.sellerId) {
        stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.SELF_BIDDING)
      }
      // Bidding on an auction whose owner is locked in not allowed
      else if (!canReceiveBids(auction.sellerId)) {
        stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.SELLER_LOCKED)
      }
      // Is the bidder allowed to bid ?
      else if (!canBid(normalizedUsersBid.bidderId)) {
        stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.BIDDER_LOCKED)
      }
      // Bidding after the end time of an auction is not allowed
      else if (normalizedUsersBid.createdAt.isAfter(auction.endsAt)) {
        stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.AUCTION_HAS_ENDED)
      }
      // Bidding on an auction that has not started is not allowed
      else if (normalizedUsersBid.createdAt.isBefore(auction.startsAt)) {
        stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.AUCTION_NOT_YET_STARTED)
      }
      // Bidding with an erroneous qty is not allowed
      else if (normalizedUsersBid.requestedQty != 1) {
        stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.WRONG_REQUESTED_QTY)
      }
      // Bidding for too many auctions is not allowed
      else if (auction.stock < 1) {
        stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.NOT_ENOUGH_STOCK)
      }
      // Bidding below the auction's current price is not allowed
      else if (normalizedUsersBid.bidPrice < auction.currentPrice) {
        stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.BID_BELOW_ALLOWED_MIN)
      }
      // Validated bid
      else {
        stay applying BidPlaced(normalizedUsersBid) replying BidPlacedReply andThen {
          case ActiveAuction(auction) => startCloseTimer(auction)
        }

        // stay applying BidPlaced(normalizedUsersBid) replying bidPlacedWithInfoReply(stateData) // BidPlacedReply(stateData)
        // TODO How to reply with the stateData resulting from the event application ? Is this an acceptable alternative way ?
        //        applyEvent(BidPlaced(normalizedUsersBid), stateData) match {
        //          case stateDataAfter => stay applying BidPlaced(normalizedUsersBid) replying BidPlacedReply(stateDataAfter)
        //        }
      }

    // A bid was placed on an auction with bids
    case Event(PlaceBid(usersBid), ActiveAuction(auction)) if auction.takesBids && auction.bids.nonEmpty =>

      val normalizedUsersBid = normalizeUsersBid(usersBid, auction)

      // A seller cannot bid on its own auctions
      if (normalizedUsersBid.bidderId == auction.sellerId) {
        stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.SELF_BIDDING)
      }
      // Bidding on an auction whose owner is locked in not allowed
      else if (!canReceiveBids(auction.sellerId)) {
        stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.SELLER_LOCKED)
      }
      // Is the bidder allowed to bid ?
      else if (!canBid(normalizedUsersBid.bidderId)) {
        stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.BIDDER_LOCKED)
      }
      // Bidding after the end time of an auction is not allowed
      else if (normalizedUsersBid.createdAt.isAfter(auction.endsAt)) {
        stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.AUCTION_HAS_ENDED)
      }
      // Bidding on an auction that has not started is not allowed
      else if (normalizedUsersBid.createdAt.isBefore(auction.startsAt)) {
        stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.AUCTION_NOT_YET_STARTED)
      }
      // Bidding with an erroneous qty is not allowed
      else if (normalizedUsersBid.requestedQty != 1) {
        stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.WRONG_REQUESTED_QTY)
      }
      // Bidding for too many auctions is not allowed
      else if (auction.stock < 1) {
        stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.NOT_ENOUGH_STOCK)
      }
      // Bidding below the auction's current price is not allowed
      else if (normalizedUsersBid.bidPrice < auction.currentPrice) {
        stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.BID_BELOW_ALLOWED_MIN)
      }
      // Bidding yourself as the highest bidder and below your max price is not allowed
      else if (normalizedUsersBid.bidderId == auction.bids.head.bidderId && normalizedUsersBid.bidPrice < auction.bids.head.bidMaxPrice) {
        stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.HIGHEST_BIDDER_BIDS_BELOW_HIS_MAX_PRICE)
      }
      // Validated bid
      else {
        stay applying BidPlaced(normalizedUsersBid) replying BidPlacedReply andThen {
          case ActiveAuction(auction) => startCloseTimer(auction)
        }
      }

    // A bid was placed on a fixed price auction
    case Event(PlaceBid(usersBid), ActiveAuction(auction)) if !auction.takesBids =>

      val normalizedUsersBid = normalizeUsersBid(usersBid, auction)

      // A seller cannot bid on its own auctions
      if (normalizedUsersBid.bidderId == auction.sellerId) {
        stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.SELF_BIDDING)
      }
      // Bidding on an auction whose owner is locked in not allowed
      else if (!canReceiveBids(auction.sellerId)) {
        stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.SELLER_LOCKED)
      }
      // Is the bidder allowed to bid ?
      else if (!canBid(normalizedUsersBid.bidderId)) {
        stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.BIDDER_LOCKED)
      }
      // Bidding after the end time of an auction is not allowed
      else if (normalizedUsersBid.createdAt.isAfter(auction.endsAt)) {
        stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.AUCTION_HAS_ENDED)
      }
      // Bidding on an auction that has not started is not allowed
      else if (normalizedUsersBid.createdAt.isBefore(auction.startsAt)) {
        stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.AUCTION_NOT_YET_STARTED)
      }
      // Bidding with an erroneous qty is not allowed
      else if (normalizedUsersBid.requestedQty < 1) {
        stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.WRONG_REQUESTED_QTY)
      }
      // Bidding for too many auctions is not allowed
      else if ((auction.stock - normalizedUsersBid.requestedQty) < 0) {
        stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.NOT_ENOUGH_STOCK)
      }
      // Bidding below the auction's current price is not allowed
      else if (normalizedUsersBid.bidPrice != auction.currentPrice) {
        stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.WRONG_BID_PRICE)
      }
      // Validated bid
      else {
        auction.stock - normalizedUsersBid.requestedQty match {
          case remainingStock if remainingStock > 0 =>
            goto(ClosedState) applying BidPlaced(normalizedUsersBid) replying BidPlacedReply andThen {
              case ActiveAuction(updatedAuction) =>
                // Clone the auction
                context.parent ! CloneAuction(updatedAuction, remainingStock, Instant.now())
            }

          case remainingStock =>
            goto(ClosedState) applying BidPlaced(normalizedUsersBid) replying AuctionClosedReply(AuctionReason.BID_NO_REMAINING_STOCK)
        }
      }
    case Event(evt: CloseAuction, ActiveAuction(_)) =>
      goto(ClosedState) applying AuctionClosed(evt) replying AuctionClosedReply(evt.reason)

    case Event(evt: CloseAuctionByTimer, _) =>
      goto(ClosedState) applying AuctionClosed(evt)
  }

  //
  // 	 #####  #       #######  #####  ####### ######
  // 	#     # #       #     # #     # #       #     #
  // 	#       #       #     # #       #       #     #
  // 	#       #       #     #  #####  #####   #     #
  // 	#       #       #     #       # #       #     #
  // 	#     # #       #     # #     # #       #     #
  // 	 #####  ####### #######  #####  ####### ######
  //
  when(ClosedState) {
    // A bid was placed on an auction
    case Event(PlaceBid(usersBid), _) =>
      stay replying BidRejectedReply(usersBid, BidRejectionReason.AUCTION_HAS_ENDED)
  }

  //
  // 	 #####  #     #  #####  ######  ####### #     # ######  ####### ######
  // 	#     # #     # #     # #     # #       ##    # #     # #       #     #
  // 	#       #     # #       #     # #       # #   # #     # #       #     #
  // 	 #####  #     #  #####  ######  #####   #  #  # #     # #####   #     #
  // 	      # #     #       # #       #       #   # # #     # #       #     #
  // 	#     # #     # #     # #       #       #    ## #     # #       #     #
  // 	 #####   #####   #####  #       ####### #     # ######  ####### ######
  //
  when(SuspendedState) {
    case Event(ResumeAuction(auctionId, resumedBy, startsAt, endsAt, createdAt), ActiveAuction(auction)) if auction.bids.isEmpty =>
      val updatedAuction = auction.copy(suspendedAt = None, startsAt = startsAt, endsAt = endsAt, renewalCount = auction.renewalCount + 1)
      if (startsAt.isAfter(Instant.now())) {
        goto(ScheduledState) applying AuctionScheduled(updatedAuction)
      } else {
        goto(StartedState) applying AuctionStarted(updatedAuction)
      }

    case Event(ResumeAuction(auctionId, resumedBy, startsAt, endsAt, createdAt), ActiveAuction(auction)) if auction.bids.nonEmpty =>
      // The current auction is CLOSED and a cloned auction will be created (cloneParameters)
      // TODO Clone the auction, HOW ? andThen ?
      val cloneParameters = CloneParameters(auction.stock, auction.startsAt, auction.endsAt)
      goto(ClosedState) applying AuctionClosed(auctionId, getSystemUserId, AuctionReason.RESUMED_WITH_BIDS, "", Instant.now(), Some(cloneParameters))
  }

  //
  //
  //
  onTransition {
    case from -> to =>
      Logger.info(s"Transitioning from $from to $to")
  }

  /**
    *
    * @param event           The event to apply
    * @param stateDataBefore The state data before any modification
    * @return
    */
  override def applyEvent(event: AuctionEvent, stateDataBefore: AuctionStateData): AuctionStateData = (event, stateDataBefore) match {
    case (AuctionStarted(auction), InactiveAuction) =>
      stateDataBefore.startAuction(auction)

    case (AuctionScheduled(auction), InactiveAuction) =>
      stateDataBefore.scheduleAuction(auction)

    case (bidPlaced: BidPlaced, activeAuction@ActiveAuction(auction)) if auction.takesBids && auction.bids.isEmpty =>
      // A bid was placed on an auction not holding any bids
      applyBidPlacedEventOnAuctionWithoutBids(bidPlaced, activeAuction)

    case (bidPlaced: BidPlaced, activeAuction@ActiveAuction(auction)) if auction.takesBids && auction.bids.nonEmpty =>
      // A bid was placed on an auction already holding at least one bid
      applyBidPlacedEventOnAuctionWithBids(bidPlaced, activeAuction)

    case (bidPlaced: BidPlaced, activeAuction@ActiveAuction(auction)) if !auction.takesBids =>
      // A bid was placed on a fixed price auction
      applyBidPlacedEventOnFixedPriceAuction(bidPlaced, activeAuction)

    case (ac@AuctionClosed(auctionId, closedBy, reason, comment, createdAt, Some(cloneParameters)), ActiveAuction(auction)) =>
      stateDataBefore.closeAuction(ac)

    case (e, s) =>
      // Unhandled case
      stateDataBefore
  }

  /**
    *
    * @param bidPlaced       The event to apply
    * @param stateDataBefore The state data before applying the event
    * @return
    */
  def applyBidPlacedEventOnAuctionWithoutBids(bidPlaced: BidPlaced, stateDataBefore: ActiveAuction): AuctionStateData = {
    val (isTimeExtended, endsAt) = stateDataBefore.auction.extendIf
    /**
      * If the auction has a reserve price and the user's bid price is >= reserve_price then the current_price is raised
      * to reach the value of the auction's reserve price. This allows a bidder who would be the sole bidder to win the auction.
      */
    val currentPrice = stateDataBefore.auction.reservePrice match {
      case Some(reservePrice) if bidPlaced.usersBid.bidPrice >= reservePrice => reservePrice
      case _ => stateDataBefore.auction.currentPrice
    }

    val bid = Bid(
      bidderId = bidPlaced.usersBid.bidderId,
      bidderName = bidPlaced.usersBid.bidderName,
      requestedQty = 1,
      bidPrice = currentPrice,
      bidMaxPrice = bidPlaced.usersBid.bidPrice,
      isVisible = true,
      isAuto = false,
      timeExtended = isTimeExtended,
      createdAt = bidPlaced.usersBid.createdAt
    )

    val stateDataAfter = stateDataBefore.placeBids(List(bid), endsAt, currentPrice, stateDataBefore.auction.stock, stateDataBefore.auction.originalStock)
    stateDataAfter
  }

  /**
    *
    * @param bidPlaced       The event to apply
    * @param stateDataBefore The state data before applying the event
    * @return
    */
  def applyBidPlacedEventOnAuctionWithBids(bidPlaced: BidPlaced, stateDataBefore: ActiveAuction): AuctionStateData = {
    val highestBid: Bid = stateDataBefore.auction.bids.head
    val (isTimeExtended, endsAt) = stateDataBefore.auction.extendIf

    if (bidPlaced.usersBid.bidderId == highestBid.bidderId && stateDataBefore.auction.reservePrice.isEmpty) {
      /**
        * The current highest bidder wants to raise his max bid price.
        * The auction's current price doesn't change, and the new bid isn't visible
        */
      val bid = Bid(
        bidderId = bidPlaced.usersBid.bidderId,
        bidderName = bidPlaced.usersBid.bidderName,
        requestedQty = 1,
        bidPrice = stateDataBefore.auction.currentPrice,
        bidMaxPrice = bidPlaced.usersBid.bidPrice,
        isVisible = false,
        isAuto = false,
        timeExtended = isTimeExtended,
        createdAt = bidPlaced.usersBid.createdAt
      )

      stateDataBefore.placeBids(List(bid), endsAt, stateDataBefore.auction.currentPrice, stateDataBefore.auction.stock, stateDataBefore.auction.originalStock, None)
    }
    else if (bidPlaced.usersBid.bidderId == highestBid.bidderId && stateDataBefore.auction.reservePrice.isDefined) {
      /**
        * The current highest bidder wants to raise his max bid price.
        *
        * If the users bid price is >= auction's reserve price and it's the first time we exceed the reserve price
        * then the auction's current price is raised to reach the value of the auction's reserve price
        * and the new bid is visible.
        */
      val (isBidVisible, currentPrice) = if (bidPlaced.usersBid.bidPrice >= stateDataBefore.auction.reservePrice.get) {
        if (stateDataBefore.auction.currentPrice < stateDataBefore.auction.reservePrice.get)
          (true, stateDataBefore.auction.reservePrice.get)
        else
          (false, stateDataBefore.auction.currentPrice)
      }
      else
        (false, stateDataBefore.auction.currentPrice)

      val bid = Bid(
        bidderId = bidPlaced.usersBid.bidderId,
        bidderName = bidPlaced.usersBid.bidderName,
        requestedQty = 1,
        bidPrice = currentPrice,
        bidMaxPrice = bidPlaced.usersBid.bidPrice,
        isVisible = isBidVisible,
        isAuto = false,
        timeExtended = isTimeExtended,
        createdAt = bidPlaced.usersBid.createdAt
      )

      stateDataBefore.placeBids(List(bid), endsAt, currentPrice, stateDataBefore.auction.stock, stateDataBefore.auction.originalStock, None)
    }
    else if (bidPlaced.usersBid.bidPrice <= highestBid.bidMaxPrice) {
      /**
        * Case of a bid that is greater than the auction's current price AND lower than the highest bidder max bid.
        *
        * The highest bidder keeps its position of highest bidder, and we raise the auction's current price to the
        * user's bid price
        */
      val bid = Bid(
        bidderId = bidPlaced.usersBid.bidderId,
        bidderName = bidPlaced.usersBid.bidderName,
        requestedQty = 1,
        bidPrice = bidPlaced.usersBid.bidPrice,
        bidMaxPrice = highestBid.bidMaxPrice,
        isVisible = true,
        isAuto = false,
        timeExtended = isTimeExtended,
        createdAt = bidPlaced.usersBid.createdAt
      )
      val updatedHighestBid = highestBid.copy(isVisible = true, isAuto = true, timeExtended = isTimeExtended, bidPrice = bidPlaced.usersBid.bidPrice)

      // It is MANDATORY to keep the order of the bids in the list below
      stateDataBefore.placeBids(List(updatedHighestBid, bid), endsAt, bidPlaced.usersBid.bidPrice, stateDataBefore.auction.stock, stateDataBefore.auction.originalStock, None)
    }
    else {
      /**
        * Case when the user's bid price is greater than the highest bid max value.
        * The current highest_bidder loses its status of highest bidder.
        *
        * If the auction has a reserve price and the user's bid price is >= reserve price then the
        * auction's current price is raised to reach the value of the auction's reserve price.
        */
      val currentPrice = stateDataBefore.auction.reservePrice match {
        case Some(reservePrice) if bidPlaced.usersBid.bidPrice >= reservePrice => reservePrice
        case _ => highestBid.bidMaxPrice + stateDataBefore.auction.bidIncrement
      }

      val newHighestBid = Bid(
        bidderId = bidPlaced.usersBid.bidderId,
        bidderName = bidPlaced.usersBid.bidderName,
        requestedQty = 1,
        bidPrice = currentPrice,
        bidMaxPrice = bidPlaced.usersBid.bidPrice,
        isVisible = true,
        isAuto = false,
        timeExtended = isTimeExtended,
        createdAt = bidPlaced.usersBid.createdAt
      )

      /**
        * We don't generate an automatic bid for the current highest bidder if the auction's price has already reached
        * the highest bidder max bid value
        */
      if (stateDataBefore.auction.currentPrice == highestBid.bidMaxPrice) {
        stateDataBefore.placeBids(List(newHighestBid), endsAt, currentPrice, stateDataBefore.auction.stock, stateDataBefore.auction.originalStock, None)
      }
      else {
        val newBid = highestBid.copy(isVisible = true, isAuto = true, timeExtended = isTimeExtended, bidPrice = highestBid.bidMaxPrice)
        // It is MANDATORY to keep the order of the bids in the list below
        stateDataBefore.placeBids(List(newHighestBid, newBid), endsAt, currentPrice, stateDataBefore.auction.stock, stateDataBefore.auction.originalStock, None)
      }
    }
  }

  /**
    *
    * @param bidPlaced       The event to apply
    * @param stateDataBefore The state data before applying the event
    * @return
    */
  def applyBidPlacedEventOnFixedPriceAuction(bidPlaced: BidPlaced, stateDataBefore: ActiveAuction): AuctionStateData = {

    stateDataBefore.auction.stock - bidPlaced.usersBid.requestedQty match {
      case remainingStock if remainingStock == 0 =>
        Logger.info(s"Auction ${stateDataBefore.auction.auctionId} sold for a qty of ${bidPlaced.usersBid.requestedQty}, no remaining stock")
        val bid = Bid(
          bidderId = bidPlaced.usersBid.bidderId,
          bidderName = bidPlaced.usersBid.bidderName,
          requestedQty = bidPlaced.usersBid.requestedQty,
          bidPrice = stateDataBefore.auction.currentPrice,
          bidMaxPrice = stateDataBefore.auction.currentPrice,
          isVisible = true,
          isAuto = false,
          timeExtended = false,
          bidPlaced.usersBid.createdAt
        )

        stateDataBefore.placeBids(
          bids = List(bid),
          updatedEndsAt = bidPlaced.usersBid.createdAt,
          updatedCurrentPrice = stateDataBefore.auction.currentPrice,
          updatedStock = 0,
          updatedOriginalStock = bidPlaced.usersBid.requestedQty,
          updatedClosedBy = None
        )

      case remainingStock =>
        Logger.info(s"Auction ${stateDataBefore.auction.auctionId} sold for a qty of ${bidPlaced.usersBid.requestedQty}, remaining stock is ${remainingStock}, duplicate the auction")
        val bid = Bid(
          bidderId = bidPlaced.usersBid.bidderId,
          bidderName = bidPlaced.usersBid.bidderName,
          requestedQty = 1,
          bidPrice = stateDataBefore.auction.currentPrice,
          bidMaxPrice = stateDataBefore.auction.currentPrice,
          isVisible = true,
          isAuto = false,
          timeExtended = false,
          createdAt = bidPlaced.usersBid.createdAt
        )
        /* TODO Handle duplication via closeParameters
          %FsmAuctionData{fsm_data | 	closed_by: nil,
                                      original_stock: event.requested_qty,
                                      stock: 0,
                                      clone_parameters: %{stock: new_stock,
                                                        start_date_time: fsm_data.start_date_time,
                                                        end_date_time: fsm_data.end_date_time},
                                      end_date_time: event.created_at,
                                      bids: [Map.from_struct(new_bid)]}
         */
        stateDataBefore.placeBids(
          bids = List(bid),
          updatedEndsAt = stateDataBefore.auction.endsAt,
          updatedCurrentPrice = stateDataBefore.auction.currentPrice,
          updatedStock = 0,
          updatedOriginalStock = bidPlaced.usersBid.requestedQty,
          updatedClosedBy = None
        )
    }
  }

  /**
    * Aligns the users's bid price to the auction's bidIncrement boundaries
    *
    * @param usersBid The users's bid
    * @param auction  The auction to normalize
    * @return
    */
  def normalizeUsersBid(usersBid: UsersBid, auction: Auction): UsersBid = {
    usersBid.copy(
      bidPrice = AuctionActor.boundedBidPrice(usersBid.bidPrice, auction.bidIncrement)
    )
  }

  def updateCurrentPriceAndBids(stateData: ActiveAuction, newCurrentPrice: BigDecimal, newBids: Seq[Bid]): Auction = {
    stateData.auction.copy(currentPrice = newCurrentPrice, bids = newBids ++ stateData.auction.bids)
  }

  // TODO implement by calling the user's actor
  def canReceiveBids(sellerId: UUID) = true

  // TODO implement
  def canBid(bidderId: UUID) = true // TODO implement

  /**
    * Get a unique timer name for a given auction
    *
    * @param auction The auction for which to generate a time name
    * @return
    */
  def getTimerName(prefix: String, auction: Auction): String = s"$prefix-${auction.auctionId}"

  /**
    * Starts a timer that will send a StartAuctionByTimer message at the given auction's startsAt time
    * The timer is started immediately if the startsAt is in the past
    *
    * @param auction The auction to start a timer on
    * @return The time name
    */
  def startScheduleTimer(auction: Auction): String = {
    // TODO Check if it is needed to control whether or not the auction is closed (closedBy, closedAt ...)
    //      If the startsAt is in the past, then realign startsAt/endsAt based on now()
    val secondsToWait = auction.startsAt.getEpochSecond - Instant.now().getEpochSecond match {
      case stw if stw >= 0 => stw
      case _ => 0
    }

    val timerName = getTimerName("schedule", auction)
    setTimer(timerName, StartAuctionByTimer(auction), secondsToWait.seconds, repeat = false)
    Logger.debug(s"Starting timer $timerName for Auction ${auction.auctionId}")
    timerName
  }

  /**
    * Starts a timer that will send a CloseAuctionByTimer message at the given auction's endsAt time
    * The timer is started immediately if the endsAt is in the past
    *
    * @param auction The auction to start a timer on
    * @return The time name
    */
  def startCloseTimer(auction: Auction): String = {
    // TODO Check if it is needed to control whether or not the auction is closed (closedBy, closedAt ...)
    //      If the startsAt is in the past, then realign startsAt/endsAt based on now()
    val secondsToWait = auction.endsAt.getEpochSecond - Instant.now().getEpochSecond match {
      case stw if stw >= 0 => stw
      case _ => 0
    }

    val timerName = getTimerName("close", auction)
    setTimer(timerName, CloseAuctionByTimer(auction), secondsToWait.seconds, repeat = false)
    Logger.debug(s"Starting timer $timerName for Auction ${auction.auctionId} in $secondsToWait seconds")
    timerName
  }

  // TODO Implement this function
  def getSystemUserId: UUID = UUID.randomUUID()
}

object AuctionActor {

  case object AuctionStartedReply

  case object AuctionScheduledReply

  case class AuctionClosedReply(reason: AuctionReason)

  case class AuctionSuspendedReply(reason: AuctionReason)

  trait BidPlacedReply

  object BidPlacedReply {
    def apply(stateData: => AuctionStateData) = stateData match {
      case ActiveAuction(auction) =>
        new BidPlacedWithInfoReply(
          auctionId = auction.auctionId,
          stock = auction.stock,
          currentPrice = auction.currentPrice,
          bidCount = auction.bids.length,
          highestBidderId = auction.bids.headOption.map(_.bidderId),
          highestBidderPrice = auction.bids.headOption.map(_.bidMaxPrice)
        )
      case _ => BidPlacedReply
    }
  }

  case class BidPlacedWithInfoReply(auctionId: UUID,
                                    stock: Int,
                                    currentPrice: BigDecimal,
                                    bidCount: Int,
                                    highestBidderId: Option[UUID],
                                    highestBidderPrice: Option[BigDecimal]
                                   ) extends BidPlacedReply

  case class BidRejectedReply(usersBid: UsersBid, reason: BidRejectionReason)

  def props = Props(new AuctionActor)

  /**
    * Aligns a bid price to a bid increment boundary
    *
    * Ex: bidPrice=1.14 bidIncrement=0.10 -> bidPrice=1.10
    * Ex: bidPrice=1.19 bidIncrement=0.10 -> bidPrice=1.10
    * Ex: bidPrice=1.00 bidIncrement=0.10 -> bidPrice=1.00
    *
    * @param bidPrice     The bid price
    * @param bidIncrement The bid increment
    * @return
    */
  def boundedBidPrice(bidPrice: BigDecimal, bidIncrement: BigDecimal): BigDecimal = {
    BigDecimal((bidPrice / bidIncrement).toInt) * bidIncrement
  }

  def createAuctionActor(maybeActorName: Option[String] = None)(implicit system: ActorSystem) = {
    val actorName = maybeActorName.getOrElse(s"auction-${UUID.randomUUID()}")

    system.actorOf(Props(new AuctionActor()), name = actorName)
  }
}
