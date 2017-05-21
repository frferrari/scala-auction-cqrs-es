package models

/**
  * Created by Francois FERRARI on 16/05/2017
  */
object AuctionReason extends Enumeration {
  type AuctionReason = Value
  val BID_NO_REMAINING_STOCK,
  RESUMED_WITH_BIDS,
  CLOSED_BY_TIMER = Value
}
