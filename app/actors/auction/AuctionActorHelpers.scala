package actors.auction

import java.util.UUID

import actors.auctionSupervisor.AuctionSupervisor
import akka.actor.{ActorSelection, ActorSystem}

/**
  * Created by Francois FERRARI on 04/06/2017
  */
trait AuctionActorHelpers {

  def getAuctionActorPrefix = s"/user/" + AuctionSupervisor.name + "/"
  def getAuctionActorName(auctionId: UUID) = s"auction-$auctionId"

  /**
    * Returns de actor name and path that is suitable when calling the actorSelection function
    * @param auctionId The auction's UUID
    * @return A string of type "/user/AuctionSupervisor/auction-e76988bb-d8e6-4e46-86ea-d41ca460139a"
    */
  def getAuctionActorNameWithPath(auctionId: UUID): String = {
    getAuctionActorPrefix + getAuctionActorName(auctionId)
  }

  /**
    * Returns an ActorSelection corresponding to the auctionId actor
    * @param auctionId The auction's UUID
    * @param system An actor system
    * @return
    */
  def getAuctionActorSelection(auctionId: UUID)(implicit system: ActorSystem): ActorSelection = {
    system.actorSelection(getAuctionActorNameWithPath(auctionId))
  }
}
