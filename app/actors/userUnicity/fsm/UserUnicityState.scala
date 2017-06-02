package actors.userUnicity.fsm

import akka.persistence.fsm.PersistentFSM.FSMState

/**
  * Created by Francois FERRARI on 20/05/2017
  */
sealed trait UserUnicityState extends FSMState

case object AwaitingFirstUserRegistration extends UserUnicityState {
  override def identifier: String = "AwaitingFirstUserRegistration"
}

case object AwaitingNextUserRegistration extends UserUnicityState {
  override def identifier: String = "AwaitNextUserRegistration"
}
