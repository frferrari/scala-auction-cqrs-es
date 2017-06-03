package actors.userUnicity.fsm

import akka.persistence.fsm.PersistentFSM.FSMState

/**
  * Created by Francois FERRARI on 20/05/2017
  */
sealed trait UserUnicityState extends FSMState

case object AwaitingFirstUserRecording extends UserUnicityState {
  override def identifier: String = "AwaitingFirstUserRegistration"
}

case object AwaitingNextUserRecording extends UserUnicityState {
  override def identifier: String = "AwaitNextUserRegistration"
}
