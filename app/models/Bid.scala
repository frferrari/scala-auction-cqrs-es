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
  BID_BELOW_ALLOWED_MIN, WRONG_BID_PRICE,
  HIGHEST_BIDDER_BIDS_BELOW_HIS_MAX_PRICE = Value
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
//  def apply(usersBid: UsersBid, isVisible: Boolean, isAuto: Boolean, timeExtended: Boolean, bidPrice: BigDecimal) = new Bid(
//    bidderId = usersBid.bidderId,
//    bidderName = "", // TODO fill in the bidderName
//    requestedQty = usersBid.requestedQty,
//    bidPrice = bidPrice,
//    bidMaxPrice = usersBid.bidPrice,
//    isVisible = isVisible,
//    isAuto = isAuto,
//    timeExtended = timeExtended,
//    createdAt = usersBid.createdAt
//  )
}
