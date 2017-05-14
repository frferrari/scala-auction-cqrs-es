package actors.fsm

import akka.persistence.fsm.PersistentFSM.FSMState

/**
  * Created by Francois FERRARI on 13/05/2017
  */
sealed trait AuctionState extends FSMState

case object Idle extends AuctionState {
  override def identifier: String = "Idle"
}

case object Scheduled extends AuctionState {
  override def identifier: String = "Scheduled"
}

case object Started extends AuctionState {
  override def identifier: String = "Started"
}

case object Closed extends AuctionState {
  override def identifier: String = "Closed"
}

case object Suspended extends AuctionState {
  override def identifier: String = "Suspended"
}

/*
case object Sold extends AuctionState {
  // TODO Is this state really needed (kind of Closed state)
  override def identifier: String = "Sold"
}
*/
