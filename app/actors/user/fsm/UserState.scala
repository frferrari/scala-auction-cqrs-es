package actors.user.fsm

import akka.persistence.fsm.PersistentFSM.FSMState

/**
  * Created by Francois FERRARI on 20/05/2017
  */
sealed trait UserState extends FSMState

case object IdleState extends UserState {
  override def identifier: String = "Idle"
}

case object RegisteredState extends UserState {
  override def identifier: String = "Registered"
}

case object ActivatedState extends UserState {
  override def identifier: String = "Activated"
}

case object LockedState extends UserState {
  override def identifier: String = "Locked"
}

case object UnregisteredState extends UserState {
  override def identifier: String = "Unregistered"
}
