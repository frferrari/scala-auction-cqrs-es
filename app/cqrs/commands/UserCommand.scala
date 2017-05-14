package cqrs.commands

import java.time.Instant
import java.util.UUID

/**
  * Created by francois on 13/05/17.
  */
sealed trait UserCommand

case class RegisterUser(userId: UUID,
                        email: String,
                        password: String,
                        algorithm: String,
                        salt: String,
                        nickname: String,
                        // is_active: Boolean,
                        isSuperAdmin: Boolean,
                        // isLocked: Boolean,
                        receivesNewsletter: Boolean,
                        receivesRenewals: Boolean,
                        lastLoginAt: Instant,
                        // unsubscribeAt: Instant,
                        token: String,
                        currency: String,
                        lastName: String,
                        firstName: String,
                        lang: String,
                        avatar: Option[String],
                        dateOfBirth: Instant,
                        phone: Option[String],
                        mobile: Option[String],
                        fax: Option[String],
                        description: Option[String],
                        sendingCountry: Option[String],
                        invoiceName: Option[String],
                        invoiceAddress1: Option[String],
                        invoiceAddress2: Option[String],
                        invoiceZipCode: Option[String],
                        invoiceCity: Option[String],
                        invoiceCountry: Option[String],
                        vatIntra: Option[String],
                        holidayStartAt: Option[Instant],
                        holidayEndAt: Option[Instant],
                        holidayHideId: UUID,
                        bidIncrement: BigDecimal,
                        autotitleId: Option[UUID],
                        listedTimeId: Option[UUID],
                        slug: String,
                        createdAt: Instant
                       ) extends UserCommand

case class ActivateUser(userId: UUID,
                        activatedAt: Instant
                       ) extends UserCommand

case class LockUser(userId: UUID,
                    lockedAt: Instant
                   ) extends UserCommand

case class UnlockUser(userId: UUID,
                      unlockedAt: Instant
                     ) extends UserCommand

case class UnregisterUser(userId: UUID,
                          unregisteredAt: Instant
                         ) extends UserCommand
