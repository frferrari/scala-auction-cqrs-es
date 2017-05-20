package actors.user.fsm

import models.User

/**
  * Created by Francois FERRARI on 20/05/2017
  */
sealed trait UserStateData {
  def registerUser(user: User): UserStateData

  def activateUser(user: User): UserStateData

//  def lockUser(userClosed: UserClosed): UserStateData
//
//  def unlockUser(userClosed: UserClosed): UserStateData
//
//  def unregisterUser(bids: Seq[Bid], updatedEndsAt: Instant, updatedCurrentPrice: BigDecimal, updatedStock: Int, updatedOriginalStock: Int, updatedClosedBy: Option[UUID] = None): UserStateData
}

case object InactiveUser extends UserStateData {
  def registerUser(user: User): UserStateData = ActiveUser(user)

  def activateUser(user: User): UserStateData = ActiveUser(user)
}

case class ActiveUser(user: User) extends UserStateData {
  def registerUser(user: User): UserStateData = this

  def activateUser(user: User): UserStateData = this
}

case class UnregisteredUser(user: User) extends UserStateData {
  def registerUser(user: User): UserStateData = this

  def activateUser(user: User): UserStateData = this
}
