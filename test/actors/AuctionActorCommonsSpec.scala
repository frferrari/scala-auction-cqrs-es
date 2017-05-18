package actors

import java.time.Instant
import java.util.UUID

/**
  * Created by Francois FERRARI on 18/05/2017
  */
trait AuctionActorCommonsSpec {
  val (bidderAName, bidderAUUID) = ("francois", UUID.randomUUID())
  val (sellerAName, sellerAUUID) = ("emmanuel", UUID.randomUUID())

  def instantNow = Instant.now()
}
