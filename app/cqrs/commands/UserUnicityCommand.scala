package cqrs.commands

import java.time.Instant

import models.User

/**
  * Created by francois on 13/05/17.
  */
sealed trait UserUnicityCommand

case class RecordUserUnicity(user: User, createdAt: Instant = Instant.now) extends UserUnicityCommand
