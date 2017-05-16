package cqrs.events

import java.time.Instant
import java.util.UUID

import cqrs.UsersBid
import models.{Auction, CloneParameters}
import models.BidRejectionReason.BidRejectionReason
import models.Reasons.Reasons

/**
  * Created by francois on 13/05/17.
  */
sealed trait AuctionEvent

case class AuctionStarted(auction: Auction) extends AuctionEvent

case class AuctionScheduled(auction: Auction) extends AuctionEvent

case class AuctionCreated(auction: Auction) extends AuctionEvent

case class AuctionClosed(closedBy: UUID,
                         reasonId: Reasons,
                         comment: String,
                         createdAt: Instant,
                         cloneParameters: Option[CloneParameters] = None
                        ) extends AuctionEvent

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

case class BidPlaced(bidPayload: UsersBid) extends AuctionEvent

case class BidRejected(bidPayload: UsersBid, rejectionReason: BidRejectionReason) extends AuctionEvent

case class CancelRejected(auctionId: UUID,
                          cancelledBy: UUID,
                          reasonId: UUID,
                          createdAt: Instant
                         ) extends AuctionEvent

case class CloseRejected(auctionId: UUID,
                         closedBy: UUID,
                         reasonId: UUID,
                         createdAt: Instant
                        ) extends AuctionEvent

case class SuspendRejected(auctionId: UUID,
                           suspendedBy: UUID,
                           reasonId: UUID,
                           createdAt: Instant
                          ) extends AuctionEvent

case class RenewRejected(auctionId: UUID,
                         renewedBy: UUID,
                         reasonId: UUID,
                         createdAt: Instant
                        ) extends AuctionEvent

case class ResumeRejected(auctionId: UUID,
                          resumedBy: UUID,
                          reasonId: UUID,
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
                         reasonId: UUID,
                         watchedAt: Instant
                        ) extends AuctionEvent
