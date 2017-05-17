package actors.fsm

import akka.persistence.fsm.PersistentFSM.FSMState

/**
  * Created by Francois FERRARI on 13/05/2017
  */
sealed trait AuctionState extends FSMState

case object IdleState extends AuctionState {
  override def identifier: String = "Idle"
}

case object ScheduledState extends AuctionState {
  override def identifier: String = "Scheduled"
}

case object StartedState extends AuctionState {
  override def identifier: String = "Started"
}

case object ClosedState extends AuctionState {
  override def identifier: String = "Closed"
}

case object SuspendedState extends AuctionState {
  override def identifier: String = "Suspended"
}

/*
case object Sold extends AuctionState {
  // TODO Is this state really needed (kind of Closed state)
  override def identifier: String = "Sold"
}
*/
