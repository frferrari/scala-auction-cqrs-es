package priceCrawler

import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.bson.codecs.Macros

/**
  * Created by Francois FERRARI on 10/06/2017
  */
case class PriceCrawlerUrl(_id: ObjectId, url: String)

object PriceCrawlerUrl {
  val priceCrawlerUrlCodec = Macros.createCodecProvider[PriceCrawlerUrl]
}