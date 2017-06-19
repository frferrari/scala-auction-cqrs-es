package priceCrawler

import java.time.Instant

import org.bson.codecs.configuration.CodecProvider
import org.mongodb.scala.bson.codecs.Macros

/**
  * Created by Francois FERRARI on 12/06/2017
  */
case class PriceCrawlerWebsite(website: String,
                               baseUrl: String,
                               defaultUrlParameters: Seq[PriceCrawlerWebsiteParameter],
                               created_at: Instant = Instant.now()
                              )

object PriceCrawlerWebsite {
  val DCP = "DCP"

  val mongoCodec: CodecProvider = Macros.createCodecProvider[PriceCrawlerWebsite]()
}
