package cqrs.commands

import java.time.Instant

import akka.actor.ActorRef
import models.User

/**
  * Created by francois on 13/05/17.
  */
sealed trait UserUnicityCommand

case class RecordUserUnicity(user: User, theSender: ActorRef, createdAt: Instant = Instant.now) extends UserUnicityCommand
