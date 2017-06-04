package cqrs.events

import java.time.Instant

import models.User

/**
  * Created by francois on 13/05/17.
  */
sealed trait UserUnicityEvent

case class UserUnicityRecorded(user: User,
                               createdAt: Instant
                              ) extends UserUnicityEvent
