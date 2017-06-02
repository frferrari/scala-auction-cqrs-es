package actors.userUnicity

import java.time.Instant

import actors.userUnicity.UserUnicityActor.{UserUnicityEmailAlreadyRegisteredReply, UserUnicityNickNameAlreadyRegisteredReply, UserUnicityRecordedReply}
import actors.userUnicity.fsm._
import akka.actor.{Actor, Props}
import akka.persistence.fsm.PersistentFSM
import cqrs.commands.RecordUserUnicity
import cqrs.events._
import models.User
import play.api.Logger

import scala.reflect.{ClassTag, classTag}

/**
  * Created by Francois FERRARI on 20/05/2017
  */
class UserUnicityActor extends Actor with PersistentFSM[UserUnicityState, UserUnicityStateData, UserUnicityEvent] {
  override def persistenceId: String = self.path.name

  override def domainEventClassTag: ClassTag[UserUnicityEvent] = classTag[UserUnicityEvent]

  Logger.info("================ Starting UserUnicityActor")

  startWith(AwaitingFirstUserRegistration, EmptyUserUnictyList)

  when(AwaitingFirstUserRegistration) {
    case Event(cmd: RecordUserUnicity, _) =>
      Logger.info("============== RecordUserUnicity cmd received")
      goto(AwaitingNextUserRegistration) applying UserUnicityRecorded(cmd.user, cmd.createdAt) replying UserUnicityRecordedReply(cmd.user, cmd.createdAt)
  }

  when(AwaitingNextUserRegistration) {
    case Event(cmd: RecordUserUnicity, NonEmptyUserUnicityList(userUnicityList)) =>
      (userUnicityList.find(_.emailAddress == cmd.user.emailAddress), userUnicityList.find(_.nickName == cmd.user.nickName)) match {
        case (Some(_), _) =>
          stay replying UserUnicityEmailAlreadyRegisteredReply(cmd.user)

        case (_, Some(_)) =>
          stay replying UserUnicityNickNameAlreadyRegisteredReply(cmd.user)

        case _ =>
          stay applying UserUnicityRecorded(cmd.user, cmd.createdAt) replying UserUnicityRecordedReply(cmd.user, cmd.createdAt)
      }
  }

  /**
    *
    * @param event           The event to apply
    * @param stateDataBefore The state data before any modification
    * @return
    */
  override def applyEvent(event: UserUnicityEvent, stateDataBefore: UserUnicityStateData): UserUnicityStateData = (event, stateDataBefore) match {
    case (event: UserUnicityRecorded, EmptyUserUnictyList) =>
      stateDataBefore.recordUser(event.user)

    case (event: UserUnicityRecorded, NonEmptyUserUnicityList(_)) =>
      stateDataBefore.recordUser(event.user)

    case _ =>
      stateDataBefore
  }
}

object UserUnicityActor {

  case class UserUnicityEmailAlreadyRegisteredReply(user: User)

  case class UserUnicityNickNameAlreadyRegisteredReply(user: User)

  case class UserUnicityRecordedReply(user: User, createdAt: Instant)

  def props = Props[UserUnicityActor]

  final val name = "UserUnicityActor"
}