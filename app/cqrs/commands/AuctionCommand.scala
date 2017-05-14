package cqrs.commands

import java.time.Instant
import java.util.UUID

import cqrs.UsersBid
import models.{Auction, Bid}

/**
  * Created by francois on 13/05/17.
  */
sealed trait AuctionCommand

case class CreateAuction(auction: Auction) extends AuctionCommand

case class StartAuction(auction: Auction) extends AuctionCommand

case class ScheduleAuction(auction: Auction) extends AuctionCommand

case class PlaceBid(bidPayload: UsersBid) extends AuctionCommand

case class CloseAuction(auctionId: UUID,
                        closedBy: UUID,
                        reasonId: UUID,
                        comment: String,
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
