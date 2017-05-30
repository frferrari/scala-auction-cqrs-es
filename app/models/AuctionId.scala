package models

import java.util.UUID

import scala.util.Try

/**
  * Created by Francois FERRARI on 29/05/2017
  */
case class AuctionId(uuid: UUID)

object AuctionId {
  def generate = AuctionId(UUID.randomUUID())

  def fromString(s: String): Option[AuctionId] = s match {
    case AuctionIdRegex(uuid) => Try(AuctionId(UUID.fromString(uuid))).toOption
    case _ => None
  }

  private val AuctionIdRegex = """AuctionId\(([a-fA-F0-9-]{36})\)""".r
}

