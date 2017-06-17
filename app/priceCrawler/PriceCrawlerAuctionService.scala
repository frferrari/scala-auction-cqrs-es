package priceCrawler

import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mongodb.scala.bson.codecs.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.bson.codecs.Macros._
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.{Completed, MongoClient, MongoDatabase, _}

import scala.concurrent.Future

/**
  * Created by Francois FERRARI on 10/06/2017
  */
class PriceCrawlerAuctionService {
  val mongoClient: MongoClient = MongoClient()
  val database: MongoDatabase = mongoClient.getDatabase("andycot")

  // !!! IMPORTANT !!! The order in which the classOf clauses appear is important, so list first inner classes then outer classes, otherwise
  // it won't work, arghhhhhh.
  val codecRegistry = fromRegistries(fromProviders(classOf[PriceCrawlerItemPrice], classOf[PriceCrawlerAuction]), DEFAULT_CODEC_REGISTRY)
  val collection = database.getCollection[PriceCrawlerAuction]("priceCrawlerAuctions").withCodecRegistry(codecRegistry)
  collection.createIndex(Document("auctionId" -> 1, "unique" -> true))

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

    collection.find(in("auctionId", auctionIds:_*)).toFuture()
    // collection.aggregate(Seq(filter(in("auctionId", auctionIds:_*)))).toFuture()
  }
}
