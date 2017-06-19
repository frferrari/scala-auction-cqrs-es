package priceCrawler

import org.mongodb.scala.{MongoClient, MongoCollection, MongoDatabase}

import scala.concurrent.Future

/**
  * Created by Francois FERRARI on 10/06/2017
  */
class PriceCrawlerUrlService {
  val mongoClient: MongoClient = MongoClient()
  val database: MongoDatabase = mongoClient.getDatabase("andycot")

  val collection: MongoCollection[PriceCrawlerUrl] = database
    .getCollection[PriceCrawlerUrl]("priceCrawlerUrls")
    .withCodecRegistry(MongoCodec.getCodecRegistry)

  /**
    *
    * @return
    */
  def findPriceCrawlerUrls: Future[Seq[PriceCrawlerUrl]] = collection.find().toFuture()

  /**
    * Generate a list of urls given a base url and the max page number.
    * The base url must contain the "page=..." string
    *
    * @param priceCrawlerBaseUrl
    * @param maxPageNumber
    * @return
    */
  def generateAllUrls(priceCrawlerBaseUrl: PriceCrawlerUrl, maxPageNumber: Int): List[String] = {
    val regex = "page=[0-9]+".r

    def generateUrl(priceCrawlerUrl: PriceCrawlerUrl)(pageNumber: Int) =
      regex.replaceAllIn(priceCrawlerUrl.url, s"page=${pageNumber.toString}")

    (1 to maxPageNumber).map(generateUrl(priceCrawlerBaseUrl)).toList
  }
}
