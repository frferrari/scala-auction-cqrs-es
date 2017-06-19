package priceCrawler

import java.time.Instant

import org.bson.codecs.configuration.CodecProvider
import org.mongodb.scala.bson.codecs.Macros

/**
  * Created by Francois FERRARI on 12/06/2017
  */
case class PriceCrawlerAuction(auctionId: String,
                               website: String,
                               auctionUrl: String,
                               auctionTitle: String,
                               thumbnailUrl: String,
                               largeUrl: String,
                               itemPrice: PriceCrawlerItemPrice,
                               createdAt: Instant = Instant.now(),
                               checkedAt: Option[Instant] = None,
                               checkedStatus: Option[Int] = None
                              )

object PriceCrawlerAuction {
  val mongoCodec: CodecProvider = Macros.createCodecProvider[PriceCrawlerAuction]()
}
