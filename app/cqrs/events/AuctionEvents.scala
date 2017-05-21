package cqrs.events

import java.time.Instant
import java.util.UUID

import cqrs.UsersBid
import cqrs.commands.{CloseAuction, CloseAuctionByTimer}
import models.AuctionReason.AuctionReason
import models.{Auction, AuctionReason, CloneParameters}

/**
  * Created by francois on 13/05/17.
  */
sealed trait AuctionEvent

case class AuctionStarted(auction: Auction) extends AuctionEvent

case class AuctionScheduled(auction: Auction) extends AuctionEvent

case class AuctionCreated(auction: Auction) extends AuctionEvent

case class AuctionClosed(auctionId: UUID,
                         closedBy: UUID,
                         reason: AuctionReason,
                         comment: String,
                         createdAt: Instant,
                         cloneParameters: Option[CloneParameters] = None
                        ) extends AuctionEvent

object AuctionClosed {
  def apply(cmd: CloseAuction) = new AuctionClosed(cmd.auctionId, cmd.closedBy, cmd.reason, cmd.comment, cmd.createdAt)
  def apply(evt: CloseAuctionByTimer) = new AuctionClosed(evt.auction.auctionId, UUID.randomUUID(), AuctionReason.CLOSED_BY_TIMER, "", Instant.now())
}

case class AuctionRenewed(auctionId: UUID,
                          startsAt: Instant,
                          endsAt: Instant,
                          createdAt: Instant
                         ) extends AuctionEvent

case class AuctionSuspended(auctionId: UUID,
                            suspendedBy: UUID,
                            createdAt: Instant
                           ) extends AuctionEvent

case class AuctionResumed(auctionId: UUID,
                          resumedBy: UUID,
                          startsAt: Instant,
                          endsAt: Instant,
                          createdAt: Instant
                         ) extends AuctionEvent

case class AuctionRestarted(auction: Auction) extends AuctionEvent

case class AuctionSold(auctionId: UUID,
                       soldTo: UUID,
                       soldQty: Int,
                       price: BigDecimal,
                       currency: String,
                       createdAt: Instant
                      ) extends AuctionEvent

case class BidPlaced(usersBid: UsersBid) extends AuctionEvent

// case class BidRejected(bidPayload: UsersBid, rejectionReason: BidRejectionReason) extends AuctionEvent

case class CancelRejected(auctionId: UUID,
                          cancelledBy: UUID,
                          reason: AuctionReason,
                          createdAt: Instant
                         ) extends AuctionEvent

case class CloseRejected(auctionId: UUID,
                         closedBy: UUID,
                         reason: AuctionReason,
                         createdAt: Instant
                        ) extends AuctionEvent

case class SuspendRejected(auctionId: UUID,
                           suspendedBy: UUID,
                           reason: AuctionReason,
                           createdAt: Instant
                          ) extends AuctionEvent

case class RenewRejected(auctionId: UUID,
                         renewedBy: UUID,
                         reason: AuctionReason,
                         createdAt: Instant
                        ) extends AuctionEvent

case class ResumeRejected(auctionId: UUID,
                          resumedBy: UUID,
                          reason: AuctionReason,
                          createdAt: Instant
                         ) extends AuctionEvent

case class WatchersCountIncremented(auctionId: UUID,
                                    createdAt: Instant
                                   ) extends AuctionEvent

case class WatchersCountDecremented(auctionId: UUID,
                                    createdAt: Instant
                                   ) extends AuctionEvent

case class VisitorsCountIncremented(auctionId: UUID,
                                    createdAt: Instant
                                   ) extends AuctionEvent

case class AuctionWatched(userId: UUID,
                          auctionId: UUID,
                          watchedAt: Instant
                         ) extends AuctionEvent

case class AuctionUnwatched(userId: UUID,
                            auctionId: UUID,
                            unwatchedAt: Instant
                           ) extends AuctionEvent

case class WatchRejected(userId: UUID,
                         auctionId: UUID,
                         reason: AuctionReason,
                         watchedAt: Instant
                        ) extends AuctionEvent
