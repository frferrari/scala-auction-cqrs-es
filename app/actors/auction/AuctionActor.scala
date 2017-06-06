package actors.auction

import java.time.Instant
import java.util.UUID

import actors.auction.AuctionActor._
import actors.auction.fsm._
import actors.user.UserActorHelpers
import actors.user.fsm.LockedState
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.{CurrentState, SubscribeTransitionCallBack, Transition, UnsubscribeTransitionCallBack}
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
class AuctionActor()
  extends Actor
    with PersistentFSM[AuctionState, AuctionStateData, AuctionEvent]
    with AuctionActorHelpers
    with UserActorHelpers {

  val msToExtend = 5000

  /**
    * The following global variables are updated based on the seller's actor transition events
    * received after having subscribed to this user's actor events
    */
  var gSellerLocked: Boolean = false
  var gSellerActorName: String = ""
  var gSellerId: Option[UUID] = None
  var gHasSubscribedToUserEvents: Boolean = false

  override def persistenceId: String = self.path.name

  override def domainEventClassTag: ClassTag[AuctionEvent] = classTag[AuctionEvent]

  override def postStop(): Unit = {
    unsubscribeUserEvents()
  }

  startWith(fsm.IdleState, InactiveAuction)

  //  override def preStart(): Unit = {
  //    context.system.actorSelection("user-")
  //    context.system.eventStream.subscribe(self, classOf[UserLocked])
  //    context.system.eventStream.subscribe(self, classOf[UserUnlocked])
  //  }

  //
  //    ###   ######  #       #######
  //     #    #     # #       #
  //     #    #     # #       #
  //     #    #     # #       #####
  //     #    #     # #       #
  //     #    #     # #       #
  //    ###   ######  ####### #######
  //
  when(fsm.IdleState) {

    case Event(cmd: StartOrScheduleAuction, _) if cmd.auction.startsAt.isAfter(Instant.now()) =>
      gSellerId = Some(cmd.auction.sellerId)
      gSellerActorName = getUserActorNameWithPath(cmd.auction.sellerId)

      if (cmd.acknowledge) {
        goto(ScheduledState) applying AuctionScheduled(cmd.auction) replying AuctionScheduledReply andThen {
          case ActiveAuction(auction) => startScheduleTimer(auction)
        }
      } else {
        goto(ScheduledState) applying AuctionScheduled(cmd.auction) andThen {
          case ActiveAuction(auction) => startScheduleTimer(auction)
        }
      }

    case Event(cmd: StartOrScheduleAuction, _) =>
      gSellerId = Some(cmd.auction.sellerId)
      gSellerActorName = getUserActorNameWithPath(cmd.auction.sellerId)

      // Subscribe to the Seller actor transitions (Handling of the Locked/Unlocked to allow/forbid buyer's to bid when Locked)
      subscribeUserEvents(cmd.auction)

      if (cmd.acknowledge) {
        goto(StartedState) applying AuctionStarted(cmd.auction) replying AuctionStartedReply andThen {
          case ActiveAuction(auction) => startCloseTimer(auction)
        }
      } else {
        goto(StartedState) applying AuctionStarted(cmd.auction) andThen {
          case ActiveAuction(auction) => startCloseTimer(auction)
        }
      }

    case Event(cmd: StartAuction, _) =>
      gSellerId = Some(cmd.auction.sellerId)
      gSellerActorName = getUserActorNameWithPath(cmd.auction.sellerId)

      // Subscribe to the Seller actor transitions (Handling of the Locked/Unlocked to allow/forbid buyer's to bid when Locked)
      subscribeUserEvents(cmd.auction)

      goto(StartedState) applying AuctionStarted(cmd.auction) replying AuctionStartedReply andThen {
        case ActiveAuction(auction) => startCloseTimer(auction)
      }

    case Event(cmd: ScheduleAuction, _) =>
      gSellerId = Some(cmd.auction.sellerId)
      gSellerActorName = getUserActorNameWithPath(cmd.auction.sellerId)

      goto(ScheduledState) applying AuctionScheduled(cmd.auction) replying AuctionScheduledReply andThen {
        case ActiveAuction(auction) => startScheduleTimer(auction)
      }

    case Event(PlaceBid(usersBid), _) =>
      stay replying BidRejectedReply(usersBid, BidRejectionReason.AUCTION_NOT_YET_STARTED)
  }

  //
  //   #####   #####  #     # ####### ######  #     # #       ####### ######
  //  #     # #     # #     # #       #     # #     # #       #       #     #
  //  #       #       #     # #       #     # #     # #       #       #     #
  //   #####  #       ####### #####   #     # #     # #       #####   #     #
  //        # #       #     # #       #     # #     # #       #       #     #
  //  #     # #     # #     # #       #     # #     # #       #       #     #
  //   #####   #####  #     # ####### ######   #####  ####### ####### ######
  //
  when(ScheduledState) {
    case Event(cmd: StartAuction, _) =>
      subscribeUserEvents(cmd.auction)
      goto(StartedState) applying AuctionStarted(cmd.auction) replying AuctionStartedReply andThen {
        case ActiveAuction(auction) => startCloseTimer(auction)
      }

    case Event(cmd: StartAuctionByTimer, _) =>
      subscribeUserEvents(cmd.auction)
      goto(StartedState) applying AuctionStarted(cmd.auction) andThen {
        case ActiveAuction(auction) => startCloseTimer(auction)
      }

    case Event(PlaceBid(usersBid), _) =>
      stay replying BidRejectedReply(usersBid, BidRejectionReason.AUCTION_NOT_YET_STARTED)

    case Event(cmd: CloseAuction, _) =>
      goto(ClosedState) applying AuctionClosed(cmd) replying AuctionClosedReply(cmd.reason)

    case Event(cmd: SuspendAuction, _) =>
      goto(SuspendedState) applying AuctionSuspended(cmd.auctionId, cmd.suspendedBy, cmd.createdAt) replying AuctionSuspendedReply(cmd.reason)
  }

  //
  //   #####  #######    #    ######  ####### ####### ######
  //  #     #    #      # #   #     #    #    #       #     #
  //  #          #     #   #  #     #    #    #       #     #
  //   #####     #    #     # ######     #    #####   #     #
  //        #    #    ####### #   #      #    #       #     #
  //  #     #    #    #     # #    #     #    #       #     #
  //   #####     #    #     # #     #    #    ####### ######
  //
  when(StartedState) {
    // A bid was placed on an auction without bids
    case Event(PlaceBid(usersBid), ActiveAuction(auction)) if auction.takesBids && auction.bids.isEmpty =>

      val normalizedUsersBid = normalizeUsersBid(usersBid, auction)

      // Bidding after the end time of an auction is not allowed
      if (normalizedUsersBid.createdAt.isAfter(auction.endsAt)) {
        stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.AUCTION_HAS_ENDED)
      }
      // Bidding on an auction that has not started is not allowed
      else if (normalizedUsersBid.createdAt.isBefore(auction.startsAt)) {
        stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.AUCTION_NOT_YET_STARTED)
      }
      // A seller cannot bid on its own auctions
      else if (normalizedUsersBid.bidderId == auction.sellerId) {
        stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.SELF_BIDDING)
      }
      // Bidding on an auction whose owner is locked in not allowed
      else if (gSellerLocked) {
        stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.SELLER_LOCKED)
      }
      // Is the bidder allowed to bid ? This can't happen as it would be forbidden upstream
      // else if (!canBid(normalizedUsersBid.bidderId)) {
      //   stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.BIDDER_LOCKED)
      // }
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
          case ActiveAuction(activeAuction) => startCloseTimer(activeAuction)
        }
      }

    // A bid was placed on an auction with bids
    case Event(PlaceBid(usersBid), ActiveAuction(auction)) if auction.takesBids && auction.bids.nonEmpty =>

      val normalizedUsersBid = normalizeUsersBid(usersBid, auction)

      // Bidding after the end time of an auction is not allowed
      if (normalizedUsersBid.createdAt.isAfter(auction.endsAt)) {
        stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.AUCTION_HAS_ENDED)
      }
      // Bidding on an auction that has not started is not allowed
      else if (normalizedUsersBid.createdAt.isBefore(auction.startsAt)) {
        stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.AUCTION_NOT_YET_STARTED)
      }
      // A seller cannot bid on its own auctions
      else if (normalizedUsersBid.bidderId == auction.sellerId) {
        stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.SELF_BIDDING)
      }
      // Bidding on an auction whose owner is locked in not allowed
      else if (gSellerLocked) {
        stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.SELLER_LOCKED)
      }
      // Is the bidder allowed to bid ? This can't happen as it would be forbidden upstream
      // else if (!canBid(normalizedUsersBid.bidderId)) {
      //   stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.BIDDER_LOCKED)
      // }
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
          case ActiveAuction(activeAuction) => startCloseTimer(activeAuction)
        }
      }

    // A bid was placed on a fixed price auction
    case Event(PlaceBid(usersBid), ActiveAuction(auction)) if !auction.takesBids =>

      val normalizedUsersBid = normalizeUsersBid(usersBid, auction)

      // Bidding after the end time of an auction is not allowed
      if (normalizedUsersBid.createdAt.isAfter(auction.endsAt)) {
        stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.AUCTION_HAS_ENDED)
      }
      // Bidding on an auction that has not started is not allowed
      else if (normalizedUsersBid.createdAt.isBefore(auction.startsAt)) {
        stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.AUCTION_NOT_YET_STARTED)
      }
      // A seller cannot bid on its own auctions
      else if (normalizedUsersBid.bidderId == auction.sellerId) {
        stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.SELF_BIDDING)
      }
      // Bidding on an auction whose owner is locked in not allowed
      else if (gSellerLocked) {
        stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.SELLER_LOCKED)
      }
      // Is the bidder allowed to bid ? This can't happen as it would be forbidden upstream
      // else if (!canBid(normalizedUsersBid.bidderId)) {
      //   stay replying BidRejectedReply(normalizedUsersBid, BidRejectionReason.BIDDER_LOCKED)
      // }
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
              case FinishedAuction(updatedAuction) =>
                context.parent ! CloneAuction(updatedAuction, remainingStock, Instant.now())
            }

          case remainingStock =>
            goto(ClosedState) applying BidPlaced(normalizedUsersBid) replying AuctionClosedReply(AuctionReason.BID_NO_REMAINING_STOCK)
        }
      }

    case Event(cmd: CloseAuction, ActiveAuction(_)) =>
      goto(ClosedState) applying AuctionClosed(cmd) replying AuctionClosedReply(cmd.reason)

    case Event(cmd: CloseAuctionByTimer, ActiveAuction(auction)) if auction.bids.isEmpty && auction.hasAutomaticRenewal =>
      // An auction without bid and with automatic renewal needs to be restarted
      stay applying AuctionRestarted(cmd.auctionId, /* TODO restartedBy */ UUID.randomUUID(), AuctionReason.RESTARTED_BY_TIMER, cmd.createdAt) andThen {
        case ActiveAuction(restartedAuction) => startCloseTimer(restartedAuction)
      }

    case Event(cmd: CloseAuctionByTimer, ActiveAuction(auction)) =>
      // TODO Check if the seller is Locked and determine what to do when at least one bid is placed (reject bid ?)
      goto(ClosedState) applying AuctionClosed(cmd)
  }

  //
  //   #####  #       #######  #####  ####### ######
  //  #     # #       #     # #     # #       #     #
  //  #       #       #     # #       #       #     #
  //  #       #       #     #  #####  #####   #     #
  //  #       #       #     #       # #       #     #
  //  #     # #       #     # #     # #       #     #
  //   #####  ####### #######  #####  ####### ######
  //
  when(ClosedState) {
    // A bid was placed on an auction
    case Event(PlaceBid(usersBid), _) =>
      stay replying BidRejectedReply(usersBid, BidRejectionReason.AUCTION_HAS_ENDED)

    case Event(UpdateClonedTo(parentAuctionId, clonedToAuctionId, createdAt), _) =>
      stay applying ClonedToUpdated(parentAuctionId, clonedToAuctionId, createdAt)
  }

  //
  //   #####  #     #  #####  ######  ####### #     # ######  ####### ######
  //  #     # #     # #     # #     # #       ##    # #     # #       #     #
  //  #       #     # #       #     # #       # #   # #     # #       #     #
  //   #####  #     #  #####  ######  #####   #  #  # #     # #####   #     #
  //        # #     #       # #       #       #   # # #     # #       #     #
  //  #     # #     # #     # #       #       #    ## #     # #       #     #
  //   #####   #####   #####  #       ####### #     # ######  ####### ######
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

  whenUnhandled {
    case Event(GetCurrentState, stateData) =>
      stay replying CurrentStateReply(stateName, stateData)

    case Event(currentState@CurrentState(actorRef, state, _), stateData) if actorRef.path.toStringWithoutAddress == gSellerActorName =>
      gSellerLocked = if (state == LockedState) true else false
      val sellerState = if (gSellerLocked) "LOCKED" else "UNLOCKED"
      Logger.info(s"AuctionActor ${self.path.toStringWithoutAddress} has received a CurrentState event from Seller ${actorRef.path.toStringWithoutAddress}, the seller is in $sellerState state")
      stay

    case Event(transition@Transition(actorRef, fromState, toState, _), stateData) if actorRef.path.toStringWithoutAddress == gSellerActorName =>
      Logger.info(s"AuctionActor ${self.path.toStringWithoutAddress} has received a Transition event from Seller ${actorRef.path.toStringWithoutAddress} : $fromState to $toState state")

      (fromState, toState) match {
        case (_, LockedState) =>
          gSellerLocked = true
          Logger.info(s"AuctionActor ${self.path.toStringWithoutAddress} LOCKING the seller")

        case (LockedState, _) =>
          gSellerLocked = false
          Logger.info(s"AuctionActor ${self.path.toStringWithoutAddress} UNLOCKING the seller")

        case (_, _) =>
      }
      stay
  }

  //
  //  ####### ######     #    #     #  #####    ###   #######   ###   ####### #     #
  //     #    #     #   # #   ##    # #     #    #       #       #    #     # ##    #
  //     #    #     #  #   #  # #   # #          #       #       #    #     # # #   #
  //     #    ######  #     # #  #  #  #####     #       #       #    #     # #  #  #
  //     #    #   #   ####### #   # #       #    #       #       #    #     # #   # #
  //     #    #    #  #     # #    ## #     #    #       #       #    #     # #    ##
  //     #    #     # #     # #     #  #####    ###      #      ###   ####### #     #
  //
  onTransition {
    case _ -> ClosedState =>
      unsubscribeUserEvents()

    case from -> to =>
      Logger.info(s"AuctionActor ${self.path.toStringWithoutAddress} Transitioning from $from to $to")
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

    case (auctionClosed: AuctionClosed, _: ActiveAuction) =>
      stateDataBefore.closeAuction(auctionClosed)

    case (_: AuctionRestarted, ActiveAuction(auction)) =>
      stateDataBefore.restartAuction(auction)

    case (clonedToUpdated: ClonedToUpdated, finishedAuction@FinishedAuction(auction)) =>
      stateDataBefore.updateClonedTo(clonedToUpdated.parentAuctionId, clonedToUpdated.clonedToAuctionId)

    case _ =>
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

    stateDataBefore.placeBids(List(bid), isSold = false, endsAt, currentPrice, stateDataBefore.auction.stock, stateDataBefore.auction.originalStock)
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

      stateDataBefore.placeBids(List(bid), isSold = false, endsAt, stateDataBefore.auction.currentPrice, stateDataBefore.auction.stock, stateDataBefore.auction.originalStock, None)
    }
    else if (bidPlaced.usersBid.bidderId == highestBid.bidderId && stateDataBefore.auction.reservePrice.isDefined) {
      /**
        * The current highest bidder wants to raise his max bid price.
        *
        * If the user's bid price is >= auction's reserve price and it's the first time we exceed the reserve price
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

      stateDataBefore.placeBids(List(bid), isSold = false, endsAt, currentPrice, stateDataBefore.auction.stock, stateDataBefore.auction.originalStock, None)
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
      stateDataBefore.placeBids(List(updatedHighestBid, bid), isSold = false, endsAt, bidPlaced.usersBid.bidPrice, stateDataBefore.auction.stock, stateDataBefore.auction.originalStock, None)
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
        stateDataBefore.placeBids(List(newHighestBid), isSold = false, endsAt, currentPrice, stateDataBefore.auction.stock, stateDataBefore.auction.originalStock, None)
      }
      else {
        val newBid = highestBid.copy(isVisible = true, isAuto = true, timeExtended = isTimeExtended, bidPrice = highestBid.bidMaxPrice)
        // It is MANDATORY to keep the order of the bids in the list below
        stateDataBefore.placeBids(List(newHighestBid, newBid), isSold = false, endsAt, currentPrice, stateDataBefore.auction.stock, stateDataBefore.auction.originalStock, None)
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
        Logger.info(s"AuctionActor ${self.path.toStringWithoutAddress} sold for a qty of ${bidPlaced.usersBid.requestedQty}, no remaining stock")
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
          updatedClosedBy = None,
          isSold = true
        )

      case remainingStock =>
        Logger.info(s"AuctionActor ${stateDataBefore.auction.auctionId} sold for a qty of ${bidPlaced.usersBid.requestedQty}, remaining stock is $remainingStock, CLONE the auction")

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

        stateDataBefore.placeBids(
          bids = List(bid),
          updatedEndsAt = stateDataBefore.auction.endsAt,
          updatedCurrentPrice = stateDataBefore.auction.currentPrice,
          updatedStock = 0,
          updatedOriginalStock = bidPlaced.usersBid.requestedQty,
          updatedClosedBy = None,
          isSold = true
        )
    }
  }

  /**
    *
    * @param auction The auction who wants to subscribe to user actor events
    */
  def subscribeUserEvents(auction: Auction): Unit = {
    val userActor = getUserActorSelection(auction.sellerId)(context.system)

    // See https://github.com/akka/akka/blob/master/akka-actor/src/main/scala/akka/actor/FSM.scala
    userActor ! SubscribeTransitionCallBack(self)
    gHasSubscribedToUserEvents = true
    Logger.info(s"AuctionActor ${self.path.toStringWithoutAddress} has SUBSCRIBED to $gSellerActorName Transition events")
  }

  def unsubscribeUserEvents(): Unit = (gHasSubscribedToUserEvents, gSellerId) match {
    case (true, Some(sellerId)) =>
      val userActor = getUserActorSelection(sellerId)(context.system)

      // See https://github.com/akka/akka/blob/master/akka-actor/src/main/scala/akka/actor/FSM.scala
      userActor ! UnsubscribeTransitionCallBack(self)

      gHasSubscribedToUserEvents = false
      gSellerId = None
      Logger.info(s"AuctionActor ${self.path.toStringWithoutAddress} has UNSUBSCRIBED to $gSellerActorName Transition events")

    case _ =>
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

  def canBid(bidderId: UUID) = true // TODO implement this in the websocket command handler

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
    Logger.debug(s"AuctionActor ${self.path.toStringWithoutAddress} Starting STARTAUCTION timer $timerName in $secondsToWait seconds")
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
    setTimer(timerName, CloseAuctionByTimer(auction.auctionId, Instant.now()), secondsToWait.seconds, repeat = false)
    Logger.debug(s"AuctionActor ${self.path.toStringWithoutAddress} Starting CLOSE timer $timerName in $secondsToWait seconds")
    timerName
  }

  // TODO Implement this function
  def getSystemUserId: UUID = UUID.randomUUID()
}

object AuctionActor extends AuctionActorHelpers {

  case object AuctionStartedReply

  case object AuctionScheduledReply

  case class AuctionClosedReply(reason: AuctionReason)

  case class AuctionSuspendedReply(reason: AuctionReason)

  trait BidPlacedReply

  object BidPlacedReply {
    def apply(stateData: => AuctionStateData) = stateData match {
      case ActiveAuction(auction) =>
        BidPlacedWithInfoReply(
          auctionId = auction.auctionId,
          stock = auction.stock,
          currentPrice = auction.currentPrice,
          bidCount = auction.bids.length,
          highestBidderId = auction.bids.headOption.map(_.bidderId),
          highestBidderPrice = auction.bids.headOption.map(_.bidMaxPrice)
        )

      case _ =>
        BidPlacedReply
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

  case class CurrentStateReply(state: AuctionState, stateData: AuctionStateData)

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

  def createAuctionActor(auction: Auction)(implicit system: ActorSystem): ActorRef = {
    val name = getAuctionActorName(auction.auctionId)
    Logger.info(s"Creating actor with name $name")

    system.actorOf(Props(new AuctionActor()), name = name)
  }
}
