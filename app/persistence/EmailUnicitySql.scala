package persistence

import java.time.Instant
import java.util.UUID
import javax.inject.{Inject, Singleton}

import anorm.SqlParser._
import anorm._
import models.{EmailAddress, EmailUnicity, User}
import play.api.db.DBApi

import scala.util.{Failure, Success, Try}

/**
  * Created by Francois FERRARI on 29/05/2017
  */

trait EmailUnicityRepo {
  def insert(user: User): Either[String, Long]
}

@Singleton
class EmailUnicitySql @Inject()(dbapi: DBApi) extends EmailUnicityRepo {
  private val db = dbapi.database("default")

  val simpleParser = {
    get[Option[Long]]("id") ~
    get[UUID]("userId") ~
    get[String]("email") ~
    get[Option[Instant]]("createdAt") map {
      case id ~ userId ~ email ~ createdAt => EmailUnicity(id, userId, EmailAddress(email), createdAt)
    }
  }

  def insert(user: User): Either[String, Long] = Try(
    db.withConnection { implicit connection =>
      SQL("insert into emailUnicity (userId, email) values ({userId}, {email})").on(
        'userId -> user.userId,
        'email -> user.emailAddress.email
      ).executeInsert(scalar[Long].singleOpt)
    }
  ) match {
    case Success(s) => Right(s.getOrElse(0))
    case Failure(f) => Left(f.getMessage)
  }
}
