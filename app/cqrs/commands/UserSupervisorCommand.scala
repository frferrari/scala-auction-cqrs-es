package cqrs.commands

import java.time.Instant

import models.User

/**
  * Created by francois on 13/05/17.
  */
sealed trait UserSupervisorCommand

case class CreateUser(user: User,
                      createdAt: Instant = Instant.now()
                     ) extends UserSupervisorCommand

case object GetUserCurrentState extends UserSupervisorCommand
