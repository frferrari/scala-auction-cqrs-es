package actors.userSupervisor.fsm

import actors.user.UserActor
import akka.actor.{ActorContext, ActorRef, Props}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import cqrs.commands.RegisterUser
import models.User
import play.api.Logger

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
  * Created by Francois FERRARI on 20/05/2017
  */
sealed trait UserSupervisorStateData {
  def createUser(user: User, theSender: ActorRef, userUnicityActorRef: ActorRef, context: ActorContext)(implicit ec: ExecutionContext): UserSupervisorStateData
}

case object ActiveUserSupervisor extends UserSupervisorStateData {

  implicit val timeout = Timeout(3.seconds)

  def createUser(user: User, theSender: ActorRef, userUnicityActorRef: ActorRef, context: ActorContext)(implicit ec: ExecutionContext): UserSupervisorStateData = {
    val actorName = UserActor.getUserActorName(user.userId)
    Logger.info(s"UserSupervisor is creating a UserActor actorName=$actorName email=${user.emailAddress.email} nickName=${user.nickName}")
    pipe(context.actorOf(Props(new UserActor(userUnicityActorRef)), name = actorName) ? RegisterUser(user)) to theSender
    this
  }
}
