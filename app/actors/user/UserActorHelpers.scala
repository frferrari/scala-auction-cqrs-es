package actors.user

import java.util.UUID

import actors.userSupervisor.UserSupervisor
import akka.actor.{ActorSelection, ActorSystem}

/**
  * Created by Francois FERRARI on 04/06/2017
  */
trait UserActorHelpers {

  def getUserActorPrefix = s"/user/" + UserSupervisor.name + "/"
  def getUserActorName(userId: UUID) = s"user-$userId"

  /**
    * Returns de actor name and path that is suitable when calling the actorSelection function
    * @param userId The user's UUID
    * @return A string of type "/user/UserSupervisor/user-e76988bb-d8e6-4e46-86ea-d41ca460139a"
    */
  def getUserActorNameWithPath(userId: UUID): String = {
    getUserActorPrefix + getUserActorName(userId)
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
