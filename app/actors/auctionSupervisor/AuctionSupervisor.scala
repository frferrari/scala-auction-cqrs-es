package actors.auctionSupervisor

import actors.auctionSupervisor.fsm.{AuctionSupervisorState, AuctionSupervisorStateData, _}
import akka.actor.Actor
import akka.persistence.fsm.PersistentFSM
import cqrs.commands.CreateAuction
import cqrs.events._
import play.api.libs.concurrent.InjectedActorSupport

import scala.reflect.{ClassTag, classTag}

/**
  * Created by Francois FERRARI on 20/05/2017
  */
class AuctionSupervisor()
  extends Actor
    with PersistentFSM[AuctionSupervisorState, AuctionSupervisorStateData, AuctionSupervisorEvent]
    with InjectedActorSupport {

  override def persistenceId: String = self.path.name

  override def domainEventClassTag: ClassTag[AuctionSupervisorEvent] = classTag[AuctionSupervisorEvent]

  startWith(ActiveState, ActiveAuctionSupervisor)

  when(ActiveState) {
    case Event(CreateAuction(auction, createdAt), _) =>
      stay applying AuctionCreated(auction, sender(), createdAt)
  }

  /**
    *
    * @param event           The event to apply
    * @param stateDataBefore The state data before any modification
    * @return
    */
  override def applyEvent(event: AuctionSupervisorEvent, stateDataBefore: AuctionSupervisorStateData): AuctionSupervisorStateData = (event, stateDataBefore) match {
    case (AuctionCreated(user, theSender, _), ActiveAuctionSupervisor) =>
      stateDataBefore.createAuction(user, theSender, context)(context.dispatcher)

    case _ =>
      stateDataBefore
  }
}

object AuctionSupervisor {
  val name = "AuctionSupervisor"
}
