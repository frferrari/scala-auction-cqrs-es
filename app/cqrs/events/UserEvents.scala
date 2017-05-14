package cqrs.events

import java.time.Instant
import java.util.UUID

/**
  * Created by francois on 13/05/17.
  */
sealed trait UserEvent

case class UserRegistered(userId: UUID,
                          email: String,
                          password: String,
                          algorithm: String,
                          salt: String,
                          nickname: String,
                          // isActive: Boolean,
                          isSuperAdmin: Boolean,
                          // is_locked: nil,
                          receivesNewsletter: Boolean,
                          receivesRenewals: Boolean,
                          lastLoginAt: Instant,
                          // unsubscribe_at: nil,
                          token: String,
                          currencyId: UUID,
                          last_name: String,
                          first_name: String,
                          lang: String,
                          avatar: Option[String],
                          dateOfBirth: Instant,
                          phone: Option[String],
                          mobile: Option[String],
                          fax: Option[String],
                          description: String,
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
                          autotitleId: UUID,
                          listedTimeId: UUID,
                          slug: String,
                          createdAt: Instant
                         ) extends UserEvent

case class AccountActivated(userId: UUID,
                            activatedAt: Instant
                           ) extends UserEvent

case class AccountLocked(userId: UUID,
                         lockedAt: Instant
                        ) extends UserEvent

case class AccountUnlocked(userId: UUID,
                           unlockedAt: Instant
                          ) extends UserEvent

case class UserUnregistered(userId: UUID,
                            unregisteredAt: Instant
                           ) extends UserEvent
