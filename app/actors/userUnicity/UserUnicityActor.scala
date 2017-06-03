package actors.userUnicity

import java.time.Instant

import actors.user.fsm.{InactiveUser, UserStateData}
import actors.userUnicity.UserUnicityActor.{UserUnicityEmailAlreadyRegisteredReply, UserUnicityNickNameAlreadyRegisteredReply, UserUnicityRecordedReply}
import actors.userUnicity.fsm._
import akka.actor.{Actor, ActorRef, Props}
import akka.persistence.fsm.PersistentFSM
import cqrs.commands.RecordUserUnicity
import cqrs.events._
import models.{User, UserUnicity}

import scala.reflect.{ClassTag, classTag}

/**
  * Created by Francois FERRARI on 20/05/2017
  */
class UserUnicityActor extends Actor with PersistentFSM[UserUnicityState, UserUnicityStateData, UserUnicityEvent] {
  override def persistenceId: String = self.path.name

  override def domainEventClassTag: ClassTag[UserUnicityEvent] = classTag[UserUnicityEvent]

  startWith(AwaitingFirstUserRegistration, EmptyUserUnictyList)

  when(AwaitingFirstUserRegistration) {
    case Event(cmd: RecordUserUnicity, _) =>
      goto(AwaitingNextUserRegistration) applying UserUnicityRecorded(cmd.user, cmd.createdAt) replying UserUnicityRecordedReply(cmd.user, cmd.theSender, cmd.createdAt)
  }

  when(AwaitingNextUserRegistration) {
    case Event(RecordUserUnicity(user, theSender, createdAt), NonEmptyUserUnicityList(userUnicityList)) =>
      findUserUnicityRecord(userUnicityList, user) match {
        case (Some(_), _) =>
          stay replying UserUnicityEmailAlreadyRegisteredReply(user, theSender)

        case (_, Some(_)) =>
          stay replying UserUnicityNickNameAlreadyRegisteredReply(user, theSender)

        case _ =>
          stay applying UserUnicityRecorded(user, createdAt) replying UserUnicityRecordedReply(user, theSender, createdAt)
      }
  }

  override def applyEvent(event: UserUnicityEvent, stateDataBefore: UserUnicityStateData): UserUnicityStateData = (event, stateDataBefore) match {
    case (UserUnicityRecorded(user, _), _) =>
      stateDataBefore.recordUser(user)

    case _ =>
      stateDataBefore
  }

  def findUserUnicityRecord(userUnicityList: Seq[UserUnicity], user: User) =
    (userUnicityList.find(_.emailAddress == user.emailAddress), userUnicityList.find(_.nickName == user.nickName))
}

object UserUnicityActor {

  case class UserUnicityEmailAlreadyRegisteredReply(user: User, theSender: ActorRef)

  case class UserUnicityNickNameAlreadyRegisteredReply(user: User, theSender: ActorRef)

  case class UserUnicityRecordedReply(user: User, theSender: ActorRef, createdAt: Instant)

  def props = Props[UserUnicityActor]

  final val name = "UserUnicityActor"
}