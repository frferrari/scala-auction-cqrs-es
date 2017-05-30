package models

import java.time.{Instant, LocalDate}
import java.util.UUID

/**
  * Created by Francois FERRARI on 20/05/2017
  */
case class User(userId: UUID,
                emailAddress: EmailAddress,
                password: String,

                isSuperAdmin: Boolean,
                receivesNewsletter: Boolean,
                receivesRenewals: Boolean,

                currency: String,

                nickName: String,
                lastName: String,
                firstName: String,
                lang: String,
                avatar: String,
                dateOfBirth: LocalDate,
                phone: String,
                mobile: String,
                fax: String,
                description: String,
                sendingCountry: String,
                invoiceName: String,
                invoiceAddress1: String,
                invoiceAddress2: String,
                invoiceZipCode: String,
                invoiceCity: String,
                invoiceCountry: String,
                vatIntra: String,

                holidayStartAt: Option[Instant],
                holidayEndAt: Option[Instant],
                holidayHideId: UUID,

                bidIncrement: BigDecimal,

                // autotitleId: nil,
                listedTimeId: UUID,

                slug: String,

                watchedAuctions: Seq[UUID],

                activatedAt: Option[Instant],
                lockedAt: Option[Instant],
                lastLoginAt: Option[Instant],
                unregisteredAt: Option[Instant],
                createdAt: Instant,
                updatedAt: Option[Instant]
               )
