package actors.userSupervisor.fsm

import akka.persistence.fsm.PersistentFSM.FSMState

/**
  * Created by Francois FERRARI on 20/05/2017
  */
sealed trait UserSupervisorState extends FSMState

case object ActiveState extends UserSupervisorState {
  override def identifier: String = "Active"
}
