package actors.user

import java.util.UUID
import javax.inject.Inject

import actors.user.UserActor.{RegistrationRejectedReply, UserRegisteredReply}
import actors.user.fsm._
import actors.userUnicity.UserUnicityActor.{UserUnicityEmailAlreadyRegisteredReply, UserUnicityNickNameAlreadyRegisteredReply, UserUnicityRecordedReply}
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.persistence.fsm.PersistentFSM
import cqrs.commands.{ActivateUser, LockUser, RecordUserUnicity, RegisterUser}
import cqrs.events._
import models.RegistrationRejectedReason.RegistrationRejectedReason
import models.{RegistrationRejectedReason, User}
import play.api.Logger
import play.api.libs.concurrent.InjectedActorSupport

import scala.concurrent.duration._
import scala.reflect.{ClassTag, classTag}

/**
  * Created by Francois FERRARI on 20/05/2017
  */
class UserActor(userUnicityActorRef: ActorRef)
  extends Actor
    with PersistentFSM[UserState, UserStateData, UserEvent]
    with InjectedActorSupport {

  override def persistenceId: String = self.path.name

  override def domainEventClassTag: ClassTag[UserEvent] = classTag[UserEvent]

  startWith(IdleState, InactiveUser)

  when(IdleState) {
    case Event(cmd: RegisterUser, _) =>
      userUnicityActorRef ! RecordUserUnicity(cmd.user)
      goto(AwaitingUserUnicityResponseState) forMax 4.seconds
  }

  when(AwaitingUserUnicityResponseState) {
    case Event(UserUnicityRecordedReply(user, createdAt), _) =>
      goto(RegisteredState) applying UserRegistered(user, createdAt) replying UserRegisteredReply

    case Event(UserUnicityEmailAlreadyRegisteredReply(user), _) =>
      Logger.warn(s"UserActor RegisterUser command rejected due to duplicate email ${user.emailAddress.email}, stopping the user actor")
      stop replying RegistrationRejectedReply(RegistrationRejectedReason.EMAIL_ALREADY_EXISTS)

    case Event(UserUnicityNickNameAlreadyRegisteredReply(user), _) =>
      Logger.warn(s"UserActor RegisterUser command rejected due to duplicate nickname ${user.nickName}, stopping the user actor")
      stop replying RegistrationRejectedReply(RegistrationRejectedReason.NICKNAME_ALREADY_EXISTS)

    case Event(StateTimeout, _) =>
      stop replying RegistrationRejectedReply(RegistrationRejectedReason.USER_UNICITY_SERVICE_TIMEOUT)
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

  case class RegistrationRejectedReply(registrationRejectedReason: RegistrationRejectedReason)

  case object UserRegisteredReply

  case object UserActivatedReply

  case object UserCantPlaceBidsReply

  case object UserCanPlaceBidsBidReply

  case object UserCantReceiveBidsReply

  def getActorName(userId: UUID) = s"user-$userId"

  def createUserActor(user: User, userUnicityActorRef: ActorRef)(implicit system: ActorSystem) = {
    val name = getActorName(user.userId)
    Logger.info(s"Creating actor with name $name")

    val actorRef: ActorRef = system.actorOf(Props(new UserActor(userUnicityActorRef)), name = name)
    Logger.info(s"UserActor $name created with actorPath ${actorRef.path.toString}")
    actorRef
  }
}