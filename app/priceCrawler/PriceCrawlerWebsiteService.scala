package priceCrawler

import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.bson.codecs.configuration.CodecRegistry
import org.mongodb.scala.bson.codecs.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.bson.codecs.Macros._
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.{Completed, MongoClient, MongoDatabase, _}

import scala.concurrent.Future

/**
  * Created by Francois FERRARI on 10/06/2017
  */
class PriceCrawlerWebsiteService {
  val mongoClient: MongoClient = MongoClient()
  val database: MongoDatabase = mongoClient.getDatabase("andycot")

  /*
   * !!! IMPORTANT !!!
   *
   * The order in which the classOf clauses appear is important,
   * so we must first list inner classes then outer classes, otherwise
   * a collection.find won't work, arghhhhhhhhhhhhhhhh
   */
  val codecRegistry: CodecRegistry = fromRegistries(fromProviders(classOf[PriceCrawlerWebsite]), DEFAULT_CODEC_REGISTRY)
  val collection: MongoCollection[PriceCrawlerWebsite] = database.getCollection[PriceCrawlerWebsite]("priceCrawlerWebsites").withCodecRegistry(codecRegistry)

  /**
    * Create a website
    * @return
    */
  def createOne(priceCrawlerWebsite: PriceCrawlerWebsite): Future[Completed] =
    collection.insertOne(priceCrawlerWebsite).head()

  /**
    * List all the websites
    * @return
    */
  def findAll: Future[Seq[PriceCrawlerWebsite]] = collection.find.toFuture()

  /**
    *
    * @param name
    * @return
    */
  def find(name: String): Future[PriceCrawlerWebsite] =
    collection.find(equal("name", name)).head()
}
