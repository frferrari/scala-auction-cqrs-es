package models

import java.time.Instant
import java.util.UUID

/**
  * Created by Francois FERRARI on 30/05/2017
  */
case class EmailUnicity(id: Option[Long], userId: UUID, emailAddress: EmailAddress, createdAt: Option[Instant])
