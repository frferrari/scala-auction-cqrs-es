package persistence

import java.time.Instant
import java.util.UUID
import javax.inject.{Inject, Singleton}

import anorm.SqlParser._
import anorm._
import models.{EmailAddress, User, UserUnicity}
import play.api.db.DBApi

/**
  * Created by Francois FERRARI on 29/05/2017
  */

trait EmailUnicityRepo {
  def insert(user: User)
}

@Singleton
class EmailUnicitySql @Inject()(dbapi: DBApi) extends EmailUnicityRepo {
  private val db = dbapi.database("default")

  val simpleParser = {
    get[Option[Long]]("id") ~
    get[UUID]("userId") ~
    get[String]("email") ~
    get[Option[Instant]]("createdAt") map {
      case id ~ userId ~ email ~ createdAt => UserUnicity(id, userId, EmailAddress(email), createdAt)
    }
  }

  def insert(user: User) = {
    db.withConnection { implicit connection =>
      SQL("insert into emailUnicity (userId, email) values ({userId}, {email})").on(
        'userId -> user.userId,
        'email -> user.emailAddress.email
      ).executeUpdate()
    }
  }
}
