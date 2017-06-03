package actors.userUnicity

import java.time.Instant

import actors.userUnicity.UserUnicityActor.{UserUnicityEmailAlreadyRegisteredReply, UserUnicityListReply, UserUnicityNickNameAlreadyRegisteredReply, UserUnicityRecordedReply}
import actors.userUnicity.fsm._
import akka.actor.{Actor, ActorRef, Props}
import akka.persistence.fsm.PersistentFSM
import cqrs.commands.{GetUserUnicityList, RecordUserUnicity}
import cqrs.events._
import models.{User, UserUnicity}
import play.api.Logger

import scala.reflect.{ClassTag, classTag}

/**
  * Created by Francois FERRARI on 20/05/2017
  */
class UserUnicityActor extends Actor with PersistentFSM[UserUnicityState, UserUnicityStateData, UserUnicityEvent] {
  override def persistenceId: String = self.path.name

  override def domainEventClassTag: ClassTag[UserUnicityEvent] = classTag[UserUnicityEvent]

  override def preStart() = {
    Logger.info("UserUnicityActor is started (preStart)")
  }

  startWith(AwaitingFirstUserRegistration, EmptyUserUnictyList)

  when(AwaitingFirstUserRegistration) {
    case Event(cmd: RecordUserUnicity, _) =>
      goto(AwaitingNextUserRegistration) applying UserUnicityRecorded(cmd.user, cmd.createdAt) replying UserUnicityRecordedReply(cmd.user, cmd.theSender, cmd.createdAt)

    case Event(GetUserUnicityList, _) =>
      stay replying UserUnicityListReply(Nil)
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

    case Event(GetUserUnicityList, NonEmptyUserUnicityList(userUnicityList)) =>
      stay replying UserUnicityListReply(userUnicityList)
  }

  override def applyEvent(event: UserUnicityEvent, stateDataBefore: UserUnicityStateData): UserUnicityStateData = (event, stateDataBefore) match {
    case (UserUnicityRecorded(user, _), _) =>
      stateDataBefore.recordUser(user)

    case _ =>
      stateDataBefore
  }

  def findUserUnicityRecord(userUnicityList: Seq[UserUnicity], user: User): (Option[UserUnicity], Option[UserUnicity]) =
    (userUnicityList.find(_.emailAddress == user.emailAddress), userUnicityList.find(_.nickName == user.nickName))
}

object UserUnicityActor {

  case class UserUnicityEmailAlreadyRegisteredReply(user: User, theSender: ActorRef)

  case class UserUnicityNickNameAlreadyRegisteredReply(user: User, theSender: ActorRef)

  case class UserUnicityRecordedReply(user: User, theSender: ActorRef, createdAt: Instant)

  case class UserUnicityListReply(userUnicitList: Seq[UserUnicity])

  def props: Props = Props[UserUnicityActor]

  final val name = "UserUnicityActor"
}