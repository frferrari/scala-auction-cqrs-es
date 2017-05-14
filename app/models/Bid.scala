package models

import java.time.Instant
import java.util.UUID

import cqrs.UsersBid

/**
  * Created by Francois FERRARI on 13/05/2017
  */
object BidRejectionReason extends Enumeration {
  type BidRejectionReason = Value
  val SELF_BIDDING, SELLER_LOCKED, BIDDER_LOCKED,
  AUCTION_HAS_ENDED, AUCTION_NOT_YET_STARTED,
  WRONG_REQUESTED_QTY, NOT_ENOUGH_STOCK,
  BID_BELOW_ALLOWED_MIN, WRONG_BID_PRICE = Value
}

case class Bid(bidderId: UUID,
               bidderName: String,
               requestedQty: Int,
               bidPrice: BigDecimal,
               bidMaxPrice: BigDecimal,
               isVisible: Boolean,
               isAuto: Boolean,
               timeExtended: Boolean,
               createdAt: Instant
              )

object Bid {
  def apply(bidPayload: UsersBid, isVisible: Boolean, isAuto: Boolean, timeExtended: Boolean, currentPrice: BigDecimal) = new Bid(
    bidPayload.bidderId,
    "", // TODO fill in the bidderName
    bidPayload.requestedQty,
    bidPayload.bidPrice,
    currentPrice,
    isVisible,
    isAuto,
    timeExtended,
    bidPayload.createdAt
  )
}
