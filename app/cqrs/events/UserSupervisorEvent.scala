package cqrs.events

import java.time.Instant

import akka.actor.ActorRef
import models.User

/**
  * Created by francois on 13/05/17.
  */
sealed trait UserSupervisorEvent

case class UserCreated(user: User,
                       theSender: ActorRef,
                       createdAt: Instant
                      ) extends UserSupervisorEvent
