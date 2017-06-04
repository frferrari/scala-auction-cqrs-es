package actors.user

import java.util.UUID

import actors.userSupervisor.UserSupervisor
import akka.actor.{ActorSelection, ActorSystem}

/**
  * Created by Francois FERRARI on 04/06/2017
  */
trait UserActorHelpers {

  def getActorPrefix = s"/user/" + UserSupervisor.name + "/"
  def getActorName(userId: UUID) = s"user-$userId"

  /**
    * Returns de actor name and path that is suitable when calling the actorSelection function
    * @param userId The user's UUID
    * @return A string of type "/user/user-e76988bb-d8e6-4e46-86ea-d41ca460139a"
    */
  def getUserActorNameWithPath(userId: UUID): String = {
    getActorPrefix + UserActor.getActorName(userId)
  }

  /**
    * Returns an ActorSelection corresponding to the userId actor
    * @param userId The user's UUID
    * @param system An actor system
    * @return
    */
  def getUserActorSelection(userId: UUID)(implicit system: ActorSystem): ActorSelection = {
    system.actorSelection(getUserActorNameWithPath(userId))
  }
}
