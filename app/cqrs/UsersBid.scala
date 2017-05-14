package cqrs

import java.time.Instant
import java.util.UUID

/**
  * Created by Francois FERRARI on 13/05/2017
  */
case class UsersBid(auctionId: UUID,
                    bidderName: String,
                    bidderId: UUID,
                    requestedQty: Int,
                    bidPrice: BigDecimal,
                    createdAt: Instant
                   )
