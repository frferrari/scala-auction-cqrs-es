package priceCrawler

import org.mongodb.scala.model.Filters._
import org.mongodb.scala.{Completed, MongoClient, MongoDatabase, _}

import scala.concurrent.Future

/**
  * Created by Francois FERRARI on 10/06/2017
  */
class PriceCrawlerAuctionService {
  val mongoClient: MongoClient = MongoClient()
  val database: MongoDatabase = mongoClient.getDatabase("andycot")

  /*
   *
   */
  val collection: MongoCollection[PriceCrawlerAuction] = database
    .getCollection[PriceCrawlerAuction]("priceCrawlerAuctions")
    .withCodecRegistry(MongoCodec.getCodecRegistry)

  // TODO add an index --- collection.createIndex(Document("auctionId" -> 1, "unique" -> true))

  /**
    *
    * @return
    */
  def createOne(priceCrawlerAuction: PriceCrawlerAuction): Future[Completed] =
    collection.insertOne(priceCrawlerAuction).head()

  def createMany(priceCrawlerAuctions: Seq[PriceCrawlerAuction]): Future[Completed] =
    collection.insertMany(priceCrawlerAuctions).head()

  /**
    *
    * @param priceCrawlerAuctions
    * @return
    */
  def findMany(priceCrawlerAuctions: Seq[PriceCrawlerAuction]): Future[Seq[PriceCrawlerAuction]] = {
    val auctionIds = priceCrawlerAuctions.map(_.auctionId)

    collection.find(in("auctionId", auctionIds: _*)).toFuture()
  }
}
