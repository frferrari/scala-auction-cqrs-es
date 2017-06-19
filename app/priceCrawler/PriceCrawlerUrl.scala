package priceCrawler

import org.bson.codecs.configuration.CodecProvider
import org.mongodb.scala.bson.codecs.Macros

/**
  * Created by Francois FERRARI on 10/06/2017
  */
case class PriceCrawlerUrl(website: String, url: String)

object PriceCrawlerUrl {
  val mongoCodec: CodecProvider = Macros.createCodecProvider[PriceCrawlerUrl]()
}