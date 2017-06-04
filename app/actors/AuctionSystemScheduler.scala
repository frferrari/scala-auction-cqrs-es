package actors

import javax.inject.{Inject, Named}

import actors.userSupervisor.UserSupervisor
import actors.userUnicity.UserUnicityActor
import akka.actor.{ActorRef, ActorSystem, Props}
import play.api.Logger

/**
  * Created by Francois FERRARI on 04/06/2017
  */
trait ActorSystemScheduler

class AuctionSystemScheduler @Inject() (@Named(UserUnicityActor.name) userUnicityActorRef: ActorRef, system: ActorSystem) extends ActorSystemScheduler {
  val start = {
    Logger.info("AuctionSystemScheduler is starting ...")
    system.actorOf(Props(new UserSupervisor(userUnicityActorRef)), name = UserSupervisor.name)
  }
}
