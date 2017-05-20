package actors

import java.time.Instant
import java.util.UUID

/**
  * Created by Francois FERRARI on 18/05/2017
  */
trait AuctionActorCommonsSpec {
  val (bidderAName, bidderAUUID) = ("bidderA", UUID.randomUUID())
  val (bidderBName, bidderBUUID) = ("bidderB", UUID.randomUUID())
  val (bidderCName, bidderCUUID) = ("bidderC", UUID.randomUUID())

  val (sellerAName, sellerAUUID) = ("sellerA", UUID.randomUUID())

  def instantNow = Instant.now()
}
