package actors.user

import actors.user.UserUnicityActor.{UserUnicityEmailAlreadyRegisteredReply, UserUnicityNickNameAlreadyRegisteredReply}
import actors.user.fsm._
import akka.actor.Actor
import akka.persistence.fsm.PersistentFSM
import cqrs.commands.RecordUserUnicity
import cqrs.events._

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
      goto(AwaitingNextUserRegistration) applying UserUnicityRecorded(cmd.user, cmd.createdAt)
  }

  when(AwaitingNextUserRegistration) {
    case Event(cmd: RecordUserUnicity, NonEmptyUserUnicityList(userUnicityList)) =>
      (userUnicityList.find(_.emailAddress == cmd.user.emailAddress), userUnicityList.find(_.nickName == cmd.user.nickName)) match {
        case (Some(_), _) =>
          stay replying UserUnicityEmailAlreadyRegisteredReply

        case (_, Some(_)) =>
          stay replying UserUnicityNickNameAlreadyRegisteredReply

        case _ =>
          stay applying UserUnicityRecorded(cmd.user, cmd.createdAt)
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
      stateDataBefore.registerUser(event.user)

    case (event: UserUnicityRecorded, NonEmptyUserUnicityList(_)) =>
      stateDataBefore.registerUser(event.user)

    case _ =>
      stateDataBefore
  }
}

object UserUnicityActor {
  case object UserUnicityEmailAlreadyRegisteredReply
  case object UserUnicityNickNameAlreadyRegisteredReply
}