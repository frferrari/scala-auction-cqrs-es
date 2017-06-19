package priceCrawler

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
   *
   */
  val collection: MongoCollection[PriceCrawlerWebsite] = database
    .getCollection[PriceCrawlerWebsite]("priceCrawlerWebsites")
    .withCodecRegistry(MongoCodec.getCodecRegistry)

  /**
    * Create a website
    *
    * @return
    */
  def createOne(priceCrawlerWebsite: PriceCrawlerWebsite): Future[Completed] =
    collection.insertOne(priceCrawlerWebsite).head()

  /**
    * List all the websites
    *
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
