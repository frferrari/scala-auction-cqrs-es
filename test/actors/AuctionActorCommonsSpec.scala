package actors

import java.time.Instant
import java.util.UUID

import akka.testkit.TestKit
import models.Auction
import play.api.Logger

/**
  * Created by Francois FERRARI on 18/05/2017
  */
trait AuctionActorCommonsSpec {
  val (bidderAName, bidderAUUID) = ("bidderA", UUID.randomUUID())
  val (bidderBName, bidderBUUID) = ("bidderB", UUID.randomUUID())
  val (bidderCName, bidderCUUID) = ("bidderC", UUID.randomUUID())

  val (sellerAName, sellerAUUID) = ("sellerA", UUID.randomUUID())

  def instantNow: Instant = Instant.now()

  def secondsToWaitForAuctionEnd(auction: Auction, gap: Long = 5): Long = auction.endsAt.getEpochSecond - Instant.now().getEpochSecond + gap
}
