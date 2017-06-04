package cqrs.commands

import java.time.Instant
import java.util.UUID

import models.User
import models.UserReason.UserReason

/**
  * Created by francois on 13/05/17.
  */
sealed trait UserCommand

case class RegisterUser(user: User,
                        createdAt: Instant = Instant.now()
                       ) extends UserCommand

case class ActivateUser(userId: UUID,
                        activatedAt: Instant
                       ) extends UserCommand

case class LockUser(userId: UUID,
                    reason: UserReason,
                    lockedBy: UUID,
                    lockedAt: Instant
                   ) extends UserCommand

case class UnlockUser(userId: UUID,
                      unlockedAt: Instant
                     ) extends UserCommand

case class DeactivateUser(userId: UUID,
                          reason: UserReason,
                          deactivatedBy: UUID,
                          deactivatedAt: Instant
                         ) extends UserCommand
