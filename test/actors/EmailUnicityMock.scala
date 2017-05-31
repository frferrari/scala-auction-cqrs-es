package actors

import models.User
import persistence.EmailUnicityRepo

/**
  * Created by Francois FERRARI on 31/05/2017
  */
@Singleton
class EmailUnicityMock extends EmailUnicityRepo {
  val emailRepo = scala.collection.mutable.ArrayBuffer[String]()

  override def insert(user: User): Either[String, Long] = {
    emailRepo.find(_ == user.emailAddress.email) match {
      case Some(email) =>
        Left("Already exists")

      case None =>
        emailRepo +: user.emailAddress.email
        Right(1)
    }
  }
}
