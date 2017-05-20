package actors.user

import java.util.UUID

import actors.user.fsm.{IdleState, InactiveUser, UserState, UserStateData}
import akka.actor.Actor
import akka.persistence.fsm.PersistentFSM
import cqrs.events._

import scala.reflect.{ClassTag, classTag}

/**
  * Created by Francois FERRARI on 20/05/2017
  */

class UserActor() extends Actor with PersistentFSM[UserState, UserStateData, UserEvent] {
  override def persistenceId: String = self.path.name

  override def domainEventClassTag: ClassTag[UserEvent] = classTag[UserEvent]

  startWith(IdleState, InactiveUser)

  /**
    *
    * @param event           The event to apply
    * @param stateDataBefore The state data before any modification
    * @return
    */
  override def applyEvent(event: UserEvent, stateDataBefore: UserStateData): UserStateData = (event, stateDataBefore) match {
    case (user: UserRegistered, InactiveUser) =>
      stateDataBefore

    case _ =>
      stateDataBefore
  }

  // TODO Implement this function
  def getSystemUserId: UUID = UUID.randomUUID()
}
