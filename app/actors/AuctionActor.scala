package actors

import java.time.Instant
import java.util.UUID

import actors.fsm.{InactiveAuction, _}
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.persistence.fsm.PersistentFSM
import cqrs.UsersBid
import cqrs.commands._
import cqrs.events._
import models.{Auction, AuctionType, Bid, BidRejectionReason}
import play.api.Logger

import scala.reflect._

/**
  * Created by Francois FERRARI on 13/05/2017
  */

object Test {
  def main(args: Array[String]): Unit = {
    Logger.info("Main is starting")

    val system = ActorSystem("andycotSystem")
    val auctionActor: ActorRef = system.actorOf(Props(new AuctionActor()), name = "auctionActor")

    val (bidderAName, bidderAUUID) = ("francois", UUID.randomUUID())
    val (sellerAName, sellerAUUID) = ("emmanuel", UUID.randomUUID())

    val instantNow = Instant.now()

    val auction = new Auction(
      Some(UUID.randomUUID()), None, None, sellerAUUID,
      UUID.randomUUID(), UUID.randomUUID(), AuctionType.AUCTION,
      "Eiffel tower", "", 2010,
      UUID.randomUUID(), Nil, Nil, None,
      Nil,
      0.10, 0.10, 0.10, None,
      1,
      instantNow, instantNow.plusSeconds(60 * 60 * 24),
      true, true,
      0, 0, 0,
      "EUR",
      None, Nil,
      None, None,
      instantNow
    )

    auctionActor ! StartAuction(auction)
    // auctionActor ! ScheduleAuction(auction)
    // auctionActor ! CloseAuction(auction.auctionId.get, UUID.randomUUID(), UUID.randomUUID(), "Closed manually", Instant.now())
    auctionActor ! PlaceBid(UsersBid(UUID.randomUUID(), bidderAName, bidderAUUID, 1, 1.00, Instant.now()))

    Thread.sleep(60000)

    system.terminate()
  }
}

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
      Logger.debug(s"Idle -> Started for auction ${evt.auction.auctionId}")
      goto(Started) applying AuctionStarted(evt.auction)
    }

    case Event(evt: ScheduleAuction, _) => {
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
    // A bid was placed on an auction
    case Event(PlaceBid(usersBid), ActiveAuction(auction)) if auction.takesBids => {

      val normalizedUsersBid = normalizeUsersBid(usersBid, auction)

      Logger.info(s"Started PlaceBid($normalizedUsersBid)")

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
        stay applying BidPlaced(normalizedUsersBid) andThen {
          case ActiveAuction(a) if a.stock == 1 =>
            Logger.info("sending CloseAuction")
            self ! CloseAuction(a.auctionId.get, UUID.randomUUID(), UUID.randomUUID(), "Closed manually", Instant.now())
        }
      }
    }

    // A bid was placed on a fixed price auction
    case Event(PlaceBid(usersBid), ActiveAuction(auction)) if !auction.takesBids => {

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

    case Event(CloseAuction(auctionId, closedBy, reasonId, comment, createdAt), ActiveAuction(auction)) => {
      Logger.info("Closing Auction")
      goto(Closed)
    }
  }

  //
  //
  when(Closed) {
    // A bid was placed on an auction
    case Event(PlaceBid(usersBid), ActiveAuction(auction)) if auction.takesBids => {
      stay
    }
  }

  /**
    *
    * @param event
    * @param stateDataBefore
    * @return
    */
  override def applyEvent(event: AuctionEvent, stateDataBefore: AuctionStateData): AuctionStateData = (event, stateDataBefore) match {
    case (AuctionStarted(auction), InactiveAuction) =>
      stateDataBefore.startAuction(auction)

    case (AuctionScheduled(auction), InactiveAuction) =>
      stateDataBefore.scheduleAuction(auction)

    case (AuctionClosed(auctionId, closedBy, reasonId, comment, createdAt), ActiveAuction(auction)) =>
      stateDataBefore.closeAuction(closedBy, createdAt)

    case (BidPlaced(usersBid), ActiveAuction(auction)) if auction.takesBids && auction.bids.isEmpty =>
      // A bid was placed on an auction not holding any bids
      applyBidPlacedEventOnAuctionWithoutBids(event, stateDataBefore, usersBid, auction)

    case (BidPlaced(usersBid), ActiveAuction(auction)) if auction.takesBids && auction.bids.nonEmpty =>
      // A bid was placed on an auction already holding at least one bid
      applyBidPlacedEventOnAuctionWithBids(event, stateDataBefore, usersBid, auction)

    case (BidPlaced(usersBid), ActiveAuction(auction)) if !auction.takesBids =>
      // A bid was placed on a fixed price auction
      applyBidPlacedEventOnFixedPriceAuction(event, stateDataBefore, usersBid, auction)

    case (_, _) =>
      // Unhandled case
      stateDataBefore
  }

  /**
    *
    * @param event
    * @param stateDataBefore
    * @param usersBid
    * @param auction
    * @return
    */
  def applyBidPlacedEventOnAuctionWithoutBids(event: AuctionEvent, stateDataBefore: AuctionStateData, usersBid: UsersBid, auction: Auction) = {
    val (isTimeExtended, endsAt) = auction.extendIf
    /**
      * If the auction has a reserve price and the user's bid price is >= reserve_price then the current_price is raised
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
    *
    * @param event
    * @param stateDataBefore
    * @param usersBid
    * @param auction
    * @return
    */
  def applyBidPlacedEventOnAuctionWithBids(event: AuctionEvent, stateDataBefore: AuctionStateData, usersBid: UsersBid, auction: Auction) = {
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
    *
    * @param event
    * @param stateDataBefore
    * @param usersBid
    * @param auction
    * @return
    */
  def applyBidPlacedEventOnFixedPriceAuction(event: AuctionEvent, stateDataBefore: AuctionStateData, usersBid: UsersBid, auction: Auction) = {
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

  /**
    * Aligns the users's bid price to the auction's bidIncrement boundaries
    *
    * @param usersBid
    * @param auction
    * @return
    */
  def normalizeUsersBid(usersBid: UsersBid, auction: Auction) = {
    usersBid.copy(
      bidPrice = AuctionActor.boundedBidPrice(usersBid.bidPrice, auction.bidIncrement)
    )
  }

  def updateCurrentPriceAndBids(stateData: ActiveAuction, newCurrentPrice: BigDecimal, newBids: Seq[Bid]) = {
    stateData.auction.copy(currentPrice = newCurrentPrice, bids = newBids ++ stateData.auction.bids)
  }

  // TODO implement by calling the user's actor
  def canReceiveBids(sellerId: UUID) = true

  // TODO implement
  def canBid(bidderId: UUID) = true // TODO implement
}

object AuctionActor {
  def props = Props(new AuctionActor)

  /**
    * Aligns a bid price to a bid increment boundary
    *
    * Ex: bidPrice=1.14 bidIncrement=0.10 -> bidPrice=1.10
    * Ex: bidPrice=1.19 bidIncrement=0.10 -> bidPrice=1.10
    * Ex: bidPrice=1.00 bidIncrement=0.10 -> bidPrice=1.00
    *
    * @param bidPrice
    * @param bidIncrement
    * @return
    */
  def boundedBidPrice(bidPrice: BigDecimal, bidIncrement: BigDecimal) = {
    BigDecimal((bidPrice / bidIncrement).toInt) * bidIncrement
  }
}