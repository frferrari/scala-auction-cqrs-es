package priceCrawler

import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mongodb.scala.bson.codecs.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.bson.codecs.Macros._
import org.mongodb.scala.{MongoClient, MongoDatabase}

/**
  * Created by Francois FERRARI on 10/06/2017
  */
class PriceCrawlerUrlService {
  val mongoClient: MongoClient = MongoClient()
  val database: MongoDatabase = mongoClient.getDatabase("andycot")

  val codecRegistry = fromRegistries(fromProviders(classOf[PriceCrawlerUrl]), DEFAULT_CODEC_REGISTRY)
  val collection = database.getCollection[PriceCrawlerUrl]("priceCrawlerUrls").withCodecRegistry(codecRegistry)

  def findPriceCrawlerUrls = collection.find().toFuture()
}
