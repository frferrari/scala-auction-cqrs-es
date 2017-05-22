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

case object ActiveState extends UserState {
  override def identifier: String = "Active"
}

case object LockedState extends UserState {
  override def identifier: String = "Locked"
}

case object DeactivatedState extends UserState {
  override def identifier: String = "Deactivated"
}
