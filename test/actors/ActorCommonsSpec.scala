package actors

import java.time.{Instant, LocalDate}
import java.util.UUID

import models._

/**
  * Created by Francois FERRARI on 18/05/2017
  */
trait ActorCommonsSpec {
  val (bidderAName, bidderAUUID) = ("bidderA", UUID.randomUUID())
  val (bidderBName, bidderBUUID) = ("bidderB", UUID.randomUUID())
  val (bidderCName, bidderCUUID) = ("bidderC", UUID.randomUUID())

  val (sellerAName, sellerAUUID) = ("sellerA", UUID.randomUUID())

  def instantNow: Instant = Instant.now()

  def secondsToWaitForAuctionStart(auction: Auction, delay: Long = 1): Long = auction.startsAt.getEpochSecond - Instant.now().getEpochSecond match {
    case stw if stw > 0 => stw + delay
    case stx => delay
  }

  def secondsToWaitForAuctionEnd(auction: Auction, delay: Long = 1): Long = auction.endsAt.getEpochSecond - Instant.now().getEpochSecond match {
    case stw if stw > 0 => stw + delay
    case stx => delay
  }

  def bidEssentials(bids: Seq[Bid]): Seq[(UUID, Int, BigDecimal, BigDecimal, Boolean, Boolean, Boolean)] = bids.map(bid => (bid.bidderId, bid.requestedQty, bid.bidPrice, bid.bidMaxPrice, bid.isVisible, bid.isAuto, bid.timeExtended))

  def makeAuction(startPrice: BigDecimal,
                  bidIncrement: BigDecimal,
                  startsAt: Instant,
                  lastsSeconds: Long,
                  hasAutomaticRenewal: Boolean,
                  hasTimeExtension: Boolean,
                  sellerId: UUID,
                  reservePrice: Option[BigDecimal] = None
                 ) =
    Auction(
      auctionId = UUID.randomUUID(),
      clonedFromAuctionId = None,
      clonedToAuctionId = None,
      cloneParameters = None,
      sellerId = sellerId,
      typeId = UUID.randomUUID(),
      listedTimeId = UUID.randomUUID(),
      AuctionType.AUCTION,
      title = "Eiffel tower",
      description = "",
      year = 2010,
      areaId = UUID.randomUUID(),
      topicIds = Nil,
      options = Nil,
      matchedId = None,
      bids = Nil,
      startPrice = startPrice,
      currentPrice = startPrice,
      bidIncrement = bidIncrement,
      reservePrice = reservePrice,
      stock = 1,
      originalStock = 1,
      startsAt = startsAt,
      suspendedAt = None,
      endsAt = startsAt.plusSeconds(lastsSeconds),
      hasAutomaticRenewal = hasAutomaticRenewal,
      hasTimeExtension = hasTimeExtension,
      renewalCount = 0,
      watchersCount = 0,
      visitorsCount = 0,
      currency = "EUR",
      slug = None,
      pictures = Nil,
      closedBy = None,
      closedAt = None,
      isSold = false,
      createdAt = instantNow
    )

  def makeFixedPriceAuction(startPrice: BigDecimal,
                            startsAt: Instant,
                            lastsSeconds: Long,
                            stock: Int,
                            hasAutomaticRenewal: Boolean,
                            sellerId: UUID
                           ) =
    Auction(
      auctionId = UUID.randomUUID(),
      clonedFromAuctionId = None,
      clonedToAuctionId = None,
      cloneParameters = None,
      sellerId = sellerId,
      typeId = UUID.randomUUID(),
      listedTimeId = UUID.randomUUID(),
      AuctionType.FIXED_PRICE,
      title = "Eiffel tower",
      description = "",
      year = 2010,
      areaId = UUID.randomUUID(),
      topicIds = Nil,
      options = Nil,
      matchedId = None,
      bids = Nil,
      startPrice = startPrice,
      currentPrice = startPrice,
      bidIncrement = 0.10,
      reservePrice = None,
      stock = stock,
      originalStock = stock,
      startsAt = startsAt,
      suspendedAt = None,
      endsAt = startsAt.plusSeconds(lastsSeconds),
      hasAutomaticRenewal = hasAutomaticRenewal,
      hasTimeExtension = false,
      renewalCount = 0,
      watchersCount = 0,
      visitorsCount = 0,
      currency = "EUR",
      slug = None,
      pictures = Nil,
      closedBy = None,
      closedAt = None,
      isSold = false,
      createdAt = instantNow
    )

  def makeUser(email: String, nickName: String, lastName: String, firstName: String) =
    User(
      userId = UUID.randomUUID(),
      emailAddress = EmailAddress(email),
      password = "",
      isSuperAdmin = false,
      receivesNewsletter = false,
      receivesRenewals = false,
      currency = "EUR",
      nickName = nickName,
      lastName = lastName,
      firstName = firstName,
      lang = "fr",
      avatar = "",
      dateOfBirth = LocalDate.of(2000, 1, 1),
      phone = "",
      mobile = "",
      fax = "",
      description = "",
      sendingCountry = "",
      invoiceName = "",
      invoiceAddress1 = "",
      invoiceAddress2 = "",
      invoiceZipCode = "",
      invoiceCity = "",
      invoiceCountry = "",
      vatIntra = "",
      holidayStartAt = None,
      holidayEndAt = None,
      holidayHideId = UUID.randomUUID(),
      bidIncrement = 0.10,
      listedTimeId = UUID.randomUUID(),
      slug = "",
      watchedAuctions = Nil,
      activatedAt = None,
      lockedAt = None,
      lastLoginAt = None,
      unregisteredAt = None,
      createdAt = Instant.now(),
      updatedAt = None
    )
}
