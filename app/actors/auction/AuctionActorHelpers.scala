package actors.auction

import java.util.UUID

import akka.actor.{ActorContext, ActorSelection}

/**
  * Created by Francois FERRARI on 04/06/2017
  */
trait AuctionActorHelpers {
  /**
    * Returns de actor name and path that is suitable when calling the actorSelection function
    * @param userId The auction's UUID
    * @return A string of type "/user/auction-e76988bb-d8e6-4e46-86ea-d41ca460139a"
    */
  def getAuctionActorNameWithPath(userId: UUID): String = {
    s"/user/" + AuctionActor.getActorName(userId)
  }

  /**
    * Returns an ActorSelection corresponding to the userId actor
    * @param userId The auction's UUID
    * @param context A supervisor context
    * @return
    */
  def getAuctionActorSelection(userId: UUID)(implicit context: ActorContext): ActorSelection = {
    context.system.actorSelection(getAuctionActorNameWithPath(userId))
  }
}
