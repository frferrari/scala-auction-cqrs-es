package cqrs.commands

import java.time.Instant
import java.util.UUID

import cqrs.UsersBid
import models.Auction
import models.AuctionReason.AuctionReason

/**
  * Created by francois on 13/05/17.
  */
sealed trait AuctionCommand

case class StartOrScheduleAuction(auction: Auction) extends AuctionCommand

case class StartAuction(auction: Auction) extends AuctionCommand

case class StartAuctionByTimer(auction: Auction) extends AuctionCommand

case class ScheduleAuction(auction: Auction) extends AuctionCommand

case class PlaceBid(bidPayload: UsersBid) extends AuctionCommand

case class CloseAuction(auctionId: UUID,
                        closedBy: UUID,
                        reason: AuctionReason,
                        comment: String,
                        createdAt: Instant
                       ) extends AuctionCommand

case class CloseAuctionByTimer(auctionId: UUID,
                               createdAt: Instant
                              ) extends AuctionCommand

case class RenewAuction(auctionId: UUID,
                        renewedBy: UUID,
                        startsAt: Instant,
                        endsAt: Instant,
                        createdAt: Instant
                       ) extends AuctionCommand

case class SuspendAuction(auctionId: UUID,
                          suspendedBy: UUID,
                          reason: AuctionReason,
                          createdAt: Instant
                         ) extends AuctionCommand

case class ResumeAuction(auctionId: UUID,
                         resumedBy: UUID,
                         startsAt: Instant,
                         endsAt: Instant,
                         createdAt: Instant
                        ) extends AuctionCommand

case class WatchAuction(auctionId: UUID,
                        userId: UUID,
                        watchedAt: Instant
                       ) extends AuctionCommand

case class UnwatchAuction(auctionId: UUID,
                          userId: UUID,
                          unwatchedAt: Instant
                         ) extends AuctionCommand

case class CloneAuction(parentAuction: Auction,
                        stock: Int,
                        startsAt: Instant,
                        createdAt: Instant
                       ) extends AuctionCommand

object CloneAuction {
  def apply(parentAucton: Auction, stock: Int, createdAt: Instant) = new CloneAuction(parentAucton, stock, createdAt, createdAt)
}

case object GetCurrentState extends AuctionCommand
