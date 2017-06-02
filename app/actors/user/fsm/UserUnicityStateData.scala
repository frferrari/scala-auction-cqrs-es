package actors.user.fsm

import models.{User, UserUnicity}

/**
  * Created by Francois FERRARI on 20/05/2017
  */
sealed trait UserUnicityStateData {
  def registerUser(user: User): UserUnicityStateData
}

case object EmptyUserUnictyList extends UserUnicityStateData {
  def registerUser(user: User): UserUnicityStateData = NonEmptyUserUnicityList(List(UserUnicity(user.emailAddress, user.nickName)))
}

case class NonEmptyUserUnicityList(userUnicity: Seq[UserUnicity]) extends UserUnicityStateData {
  def registerUser(user: User): UserUnicityStateData = NonEmptyUserUnicityList(userUnicity :+ UserUnicity(user.emailAddress, user.nickName))
}
