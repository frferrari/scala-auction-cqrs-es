package models

import java.time.Instant
import java.util.UUID

import actors.AuctionActor
import org.scalatest.Matchers._
import org.scalatestplus.play.PlaySpec

/**
  * Created by Francois FERRARI on 14/05/2017
  */
class AuctionSpec extends PlaySpec {

  val auction = Auction(
    UUID.randomUUID(),
    None, None, None,
    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
    AuctionType.AUCTION, "", "", 2017,
    UUID.randomUUID(), Nil, Nil, None, Nil,
    1.00, 1.00, 0.10, None,
    1, 1,
    Instant.now(), None, Instant.now().plusSeconds(60*60*24*10),
    true, false,
    0, 0, 0,
    "EUR", None, Nil, None, None,
    Instant.now()
  )

  "Auction" should {

    "align a bid price on a bid increment boundary of 0.10" in {
      auction.boundedBidPrice(BigDecimal(1.14), BigDecimal(0.10)) should equal(BigDecimal(1.10))
      auction.boundedBidPrice(BigDecimal(1.19), BigDecimal(0.10)) should equal(BigDecimal(1.10))
      auction.boundedBidPrice(BigDecimal(1.10), BigDecimal(0.10)) should equal(BigDecimal(1.10))
      auction.boundedBidPrice(BigDecimal(1.20), BigDecimal(0.10)) should equal(BigDecimal(1.20))
      auction.boundedBidPrice(BigDecimal(1.30), BigDecimal(0.10)) should equal(BigDecimal(1.30))
    }
    "align a bid price on a bid increment boundary of 0.20" in {
      auction.boundedBidPrice(BigDecimal(0.20), BigDecimal(0.20)) should equal(BigDecimal(0.20))
      auction.boundedBidPrice(BigDecimal(0.26), BigDecimal(0.20)) should equal(BigDecimal(0.20))
      auction.boundedBidPrice(BigDecimal(1.20), BigDecimal(0.20)) should equal(BigDecimal(1.20))
      auction.boundedBidPrice(BigDecimal(1.30), BigDecimal(0.20)) should equal(BigDecimal(1.20))
      auction.boundedBidPrice(BigDecimal(1.40), BigDecimal(0.20)) should equal(BigDecimal(1.40))
    }
    "align a bid price on a bid increment boundary of 0.25" in {
      auction.boundedBidPrice(BigDecimal(0.25), BigDecimal(0.25)) should equal(BigDecimal(0.25))
      auction.boundedBidPrice(BigDecimal(0.50), BigDecimal(0.25)) should equal(BigDecimal(0.50))
      auction.boundedBidPrice(BigDecimal(1.55), BigDecimal(0.25)) should equal(BigDecimal(1.50))
    }
    "align a bid price on a bid increment boundary of 1.00" in {
      auction.boundedBidPrice(BigDecimal(0.25), BigDecimal(1.00)) should equal(BigDecimal(0.00))
      auction.boundedBidPrice(BigDecimal(1.00), BigDecimal(1.00)) should equal(BigDecimal(1.00))
      auction.boundedBidPrice(BigDecimal(1.55), BigDecimal(1.00)) should equal(BigDecimal(1.00))
      auction.boundedBidPrice(BigDecimal(2.00), BigDecimal(1.00)) should equal(BigDecimal(2.00))
    }
  }
}
