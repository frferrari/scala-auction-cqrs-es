package priceCrawler

import org.bson.codecs.configuration.CodecProvider
import org.mongodb.scala.bson.codecs.Macros

/**
  * Created by Francois FERRARI on 13/06/2017
  */
case class PriceCrawlerItemPrice(price: BigDecimal, currency: String)

object PriceCrawlerItemPrice{
  val mongoCodec: CodecProvider = Macros.createCodecProvider[PriceCrawlerItemPrice]()
}