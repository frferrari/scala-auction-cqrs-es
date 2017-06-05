package actors

import javax.inject.{Inject, Named}

import actors.auctionSupervisor.AuctionSupervisor
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
    // TODO ??? Add a mechanism to start the AuctionSupervisor only when the UserSupervisor is ready (recovered all user actors) ?
    system.actorOf(Props(new AuctionSupervisor()), name = AuctionSupervisor.name)
  }
}
