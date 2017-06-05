package cqrs.commands

import java.time.Instant

import models.Auction

/**
  * Created by francois on 13/05/17.
  */
sealed trait AuctionSupervisorCommand

case class CreateAuction(auction: Auction,
                         createdAt: Instant = Instant.now()
                        ) extends AuctionSupervisorCommand

case object GetAuctionCurrentState extends AuctionSupervisorCommand
