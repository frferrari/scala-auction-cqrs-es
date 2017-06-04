package actors.userSupervisor

import actors.userSupervisor.fsm.{UserSupervisorState, UserSupervisorStateData, _}
import akka.actor.{Actor, ActorRef}
import akka.persistence.fsm.PersistentFSM
import cqrs.commands.CreateUser
import cqrs.events._
import play.api.libs.concurrent.InjectedActorSupport

import scala.reflect.{ClassTag, classTag}

/**
  * Created by Francois FERRARI on 20/05/2017
  */
class UserSupervisor(userUnicityActorRef: ActorRef)
  extends Actor
    with PersistentFSM[UserSupervisorState, UserSupervisorStateData, UserSupervisorEvent]
    with InjectedActorSupport {

  override def persistenceId: String = self.path.name

  override def domainEventClassTag: ClassTag[UserSupervisorEvent] = classTag[UserSupervisorEvent]

  startWith(ActiveState, ActiveUserSupervisor)

  when(ActiveState) {
    case Event(CreateUser(user, createdAt), _) =>
      stay applying UserCreated(user, sender(), createdAt)
  }

  /**
    *
    * @param event           The event to apply
    * @param stateDataBefore The state data before any modification
    * @return
    */
  override def applyEvent(event: UserSupervisorEvent, stateDataBefore: UserSupervisorStateData): UserSupervisorStateData = (event, stateDataBefore) match {
    case (UserCreated(user, theSender, _), ActiveUserSupervisor) =>
      stateDataBefore.createUser(user, theSender, userUnicityActorRef, context)(context.dispatcher)

    case _ =>
      stateDataBefore
  }
}

object UserSupervisor {
  val name = "UserSupervisor"
}
