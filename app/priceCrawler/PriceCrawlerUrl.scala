package priceCrawler

import org.mongodb.scala.bson.codecs.Macros

/**
  * Created by Francois FERRARI on 10/06/2017
  */
object PriceCrawlerWebsite {
  val DCP = "DCP"
}

case class PriceCrawlerUrl(website: String, url: String)

object PriceCrawlerUrl {
  val priceCrawlerUrlCodec = Macros.createCodecProvider[PriceCrawlerUrl]
}