package actors.auctionSupervisor.fsm

import actors.auction.AuctionActor
import akka.actor.{ActorContext, ActorRef, Props}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import cqrs.commands.StartOrScheduleAuction
import models.Auction
import play.api.Logger

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
  * Created by Francois FERRARI on 20/05/2017
  */
sealed trait AuctionSupervisorStateData {
  def createAuction(auction: Auction, theSender: ActorRef, context: ActorContext)(implicit ec: ExecutionContext): AuctionSupervisorStateData
}

case object ActiveAuctionSupervisor extends AuctionSupervisorStateData {

  implicit val timeout = Timeout(3.seconds)

  def createAuction(auction: Auction, theSender: ActorRef, context: ActorContext)(implicit ec: ExecutionContext): AuctionSupervisorStateData = {
    val actorName = AuctionActor.getActorName(auction.auctionId)
    Logger.info(s"AuctionSupervisor is creating an AuctionActor actorName=$actorName sellerId=${auction.sellerId}")
    pipe(context.actorOf(Props(new AuctionActor()), name = actorName) ? StartOrScheduleAuction(auction)) to theSender
    this
  }
}
