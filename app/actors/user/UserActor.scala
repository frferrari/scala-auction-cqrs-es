package actors.user

import java.util.UUID
import javax.inject.Inject

import actors.user.UserActor.{UserActivatedReply, UserRegisteredReply}
import actors.user.fsm._
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.persistence.fsm.PersistentFSM
import cqrs.commands.{ActivateUser, LockUser, RegisterUser}
import cqrs.events._
import models.{Auction, User}
import persistence.EmailUnicityRepo
import play.api.Logger

import scala.reflect.{ClassTag, classTag}

/**
  * Created by Francois FERRARI on 20/05/2017
  */
class UserActor extends Actor with PersistentFSM[UserState, UserStateData, UserEvent] {
  override def persistenceId: String = self.path.name

  override def domainEventClassTag: ClassTag[UserEvent] = classTag[UserEvent]

  startWith(IdleState, InactiveUser)

  when(IdleState) {
    case Event(cmd: RegisterUser, _) =>
      goto(RegisteredState) applying UserRegistered(cmd.user, cmd.createdAt) // replying UserRegisteredReply
  }

  when(RegisteredState) {
    case Event(cmd: ActivateUser, _) =>
      goto(ActiveState) applying UserActivated(cmd.userId, cmd.activatedAt) // replying UserActivatedReply

    case Event(cmd: LockUser, _) =>
      goto(LockedState) applying UserLocked(cmd.userId, cmd.reason, cmd.lockedBy, cmd.lockedAt)
  }

  when(ActiveState) {
    case Event(cmd: LockUser, _) =>
      goto(LockedState) applying UserLocked(cmd.userId, cmd.reason, cmd.lockedBy, cmd.lockedAt)

    case Event(_, _) =>
      stay
  }

  when(LockedState) {
    case Event(_, _) =>
      goto(ActiveState)
  }

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

object UserActor {

  case object UserRegisteredReply

  case object UserActivatedReply

  case object UserCantPlaceBidsReply

  case object UserCanPlaceBidsBidReply

  case object UserCantReceiveBidsReply

  def getActorName(userId: UUID) = s"user-$userId"

  def createUserActor(user: User)(implicit system: ActorSystem) = {
    val name = getActorName(user.userId)
    Logger.info(s"Creating actor with name $name")

    val actorRef: ActorRef = system.actorOf(Props(new UserActor()), name = name)
    Logger.info(s"UserActor $name created with actorPath ${actorRef.path.toString}")
    actorRef
  }
}