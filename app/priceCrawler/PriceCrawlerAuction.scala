package priceCrawler

/**
  * Created by Francois FERRARI on 12/06/2017
  */
case class PriceCrawlerAuction(auctionId: String,
                               auctionUrl: String,
                               auctionTitle: String,
                               thumbnailUrl: String,
                               largeUrl: String,
                               itemPrice: PriceCrawlerItemPrice
//                               createdAt: LocalDate = LocalDate.now(),
//                               checkedAt: Option[LocalDate] = None,
//                               createdAt: Instant = Instant.now(),
//                               checkedAt: Option[Instant] = None,
//                               checkedStatus: Option[Int] = None
                              )
