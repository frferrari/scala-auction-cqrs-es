package actors.userUnicity.fsm

import models.{User, UserUnicity}
import play.api.Logger

/**
  * Created by Francois FERRARI on 20/05/2017
  */
sealed trait UserUnicityStateData {
  def recordUser(user: User): UserUnicityStateData
}

case object EmptyUserUnictyList extends UserUnicityStateData {
  def recordUser(user: User): UserUnicityStateData = NonEmptyUserUnicityList(List(UserUnicity(user.emailAddress, user.nickName)))
}

case class NonEmptyUserUnicityList(userUnicityList: Seq[UserUnicity]) extends UserUnicityStateData {
  def recordUser(user: User): UserUnicityStateData = NonEmptyUserUnicityList(userUnicityList :+ UserUnicity(user.emailAddress, user.nickName))
}
