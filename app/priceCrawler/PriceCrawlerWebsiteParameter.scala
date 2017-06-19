package priceCrawler

import org.bson.codecs.configuration.CodecProvider
import org.mongodb.scala.bson.codecs.Macros

/**
  * Created by Francois FERRARI on 19/06/2017
  */
case class PriceCrawlerWebsiteParameter(parameter: String, pattern: Option[String])

object PriceCrawlerWebsiteParameter {
  val mongoCodec: CodecProvider = Macros.createCodecProvider[PriceCrawlerWebsiteParameter]()
}