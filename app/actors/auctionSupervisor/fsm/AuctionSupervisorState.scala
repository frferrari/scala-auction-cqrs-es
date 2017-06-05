package actors.auctionSupervisor.fsm

import akka.persistence.fsm.PersistentFSM.FSMState

/**
  * Created by Francois FERRARI on 20/05/2017
  */
sealed trait AuctionSupervisorState extends FSMState

case object ActiveState extends AuctionSupervisorState {
  override def identifier: String = "Active"
}
