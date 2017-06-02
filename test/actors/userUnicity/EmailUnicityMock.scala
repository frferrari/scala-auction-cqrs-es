package actors.userUnicity

import models.User
import persistence.EmailUnicityRepo
import play.api.Logger

/**
  * Created by Francois FERRARI on 31/05/2017
  */
class EmailUnicityMock extends EmailUnicityRepo {
  var emailRepo = scala.collection.mutable.ListBuffer[String]()
  var nickNameRepo = scala.collection.mutable.ListBuffer[String]()

  override def insert(user: User): Either[String, Long] = {

    Logger.info(s"emailRepo $emailRepo / nickNameRepo $nickNameRepo")

    findEmailAndNickname(user) match {
      case (Some(email), _) =>
        Left("Email already exists")
        
      case (_, Some(nickName)) =>
        Left("Nickname already exists")
        
      case (None, None) =>
        emailRepo = emailRepo :+ user.emailAddress.email
        nickNameRepo = nickNameRepo :+ user.nickName
        Right(1)
    }
  }

  private def findEmailAndNickname(user: User): (Option[String], Option[String]) =
    (emailRepo.find(_ == user.emailAddress.email), nickNameRepo.find(_ == user.nickName))
}
