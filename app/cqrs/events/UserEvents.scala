package cqrs.events

import java.time.Instant
import java.util.UUID

import models.User
import models.UserReason.UserReason

/**
  * Created by francois on 13/05/17.
  */
sealed trait UserEvent

case class UserRegistered(user: User,
                          createdAt: Instant
                         ) extends UserEvent

case class UserActivated(userId: UUID,
                         activatedAt: Instant
                        ) extends UserEvent

case class UserLocked(userId: UUID,
                      reason: UserReason,
                      lockedBy: UUID,
                      lockedAt: Instant
                     ) extends UserEvent

case class UserUnlocked(userId: UUID,
                        unlockedAt: Instant
                       ) extends UserEvent

case class UserDeactivated(userId: UUID,
                           reason: UserReason,
                           deactivatedBy: UUID,
                           deactivatedAt: Instant
                          ) extends UserEvent
