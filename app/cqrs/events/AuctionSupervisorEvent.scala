package cqrs.events

import java.time.Instant

import akka.actor.ActorRef
import models.Auction

/**
  * Created by francois on 13/05/17.
  */
sealed trait AuctionSupervisorEvent

case class AuctionCreated(auction: Auction,
                          theSender: ActorRef,
                          acknowledge: Boolean,
                          createdAt: Instant
                         ) extends AuctionSupervisorEvent
