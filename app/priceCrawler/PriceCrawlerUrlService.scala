package priceCrawler

import org.mongodb.scala.{MongoClient, MongoCollection, MongoDatabase}
import play.api.Logger

import scala.concurrent.Future
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

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
  def generateAllUrls(priceCrawlerBaseUrl: PriceCrawlerUrl, priceCrawlerWebsites: Seq[PriceCrawlerWebsite], maxPageNumber: Int): List[String] = {
    val priceCrawlerWebsite: Seq[PriceCrawlerWebsite] = priceCrawlerWebsites.filter(_.website == priceCrawlerBaseUrl.website)

    (1 to maxPageNumber).map(generateUrl(priceCrawlerBaseUrl, priceCrawlerWebsite)).toList
  }

  /**
    *
    * @param priceCrawlerUrl
    * @param pageNumber
    * @return
    */
  def generateUrl(priceCrawlerUrl: PriceCrawlerUrl, priceCrawlerWebsiteParameters: Seq[PriceCrawlerWebsite])(pageNumber: Int): String = {
    val pageParameter: String = f"page=$pageNumber"
    val parameters: Seq[PriceCrawlerWebsiteParameter] = priceCrawlerWebsiteParameters.flatMap(_.defaultUrlParameters) :+ PriceCrawlerWebsiteParameter(pageParameter, None)

    addUrlParameters(priceCrawlerUrl.url, toRegex(parameters))
  }

  /**
    *
    * @param parameters
    */
  def toRegex(parameters: Seq[PriceCrawlerWebsiteParameter]): Seq[(String, Option[Regex])] = {
    parameters.map {
      case PriceCrawlerWebsiteParameter(toAdd, Some(pattern)) => Try(new Regex(pattern)) match {
        case Success(regex) =>
          (toAdd, Some(regex))

        case Failure(f) =>
          Logger.warn(s"toRegex: Unable to create a regex from string $pattern", f)
          (toAdd, None)
      }

      case p@PriceCrawlerWebsiteParameter(_, None) =>
        (p.parameter, None)
    }
  }

  /**
    *
    * @param url
    * @param urlParameters
    * @return
    */
  def addUrlParameters(url: String, urlParameters: Seq[(String, Option[Regex])]): String = {
    val separator = if (url.contains("?")) "&" else "?"

    urlParameters.foldLeft((separator, url)) { case ((urlParameterSeparator, newUrl), urlParameter) =>
      addUrlParameter(newUrl, urlParameter._1, urlParameterSeparator, urlParameter._2)
    }._2
  }

  /**
    *
    * @param url
    * @param urlParameter
    * @param urlParameterSeparator
    * @param urlParameterRegex
    * @return
    */
  def addUrlParameter(url: String, urlParameter: String, urlParameterSeparator: String, urlParameterRegex: Option[Regex] = None): (String, String) = {
    urlParameterRegex.fold(addUrlParameterGivenString(url, urlParameter, urlParameterSeparator))(addUrlParameterGivenRegex(url, urlParameter, urlParameterSeparator, _))
  }

  /**
    *
    * @param url
    * @param urlParameter
    * @param urlParameterSeparator
    * @return
    */
  def addUrlParameterGivenString(url: String, urlParameter: String, urlParameterSeparator: String): (String, String) = {
    url.contains(urlParameter) match {
      case true =>
        (urlParameterSeparator, url)
      case false =>
        ("&", s"$url$urlParameterSeparator$urlParameter")
    }
  }

  /**
    *
    * @param url
    * @param urlParameter
    * @param urlParameterSeparator
    * @param urlParameterRegex
    * @return
    */
  def addUrlParameterGivenRegex(url: String, urlParameter: String, urlParameterSeparator: String, urlParameterRegex: Regex): (String, String) = {
    ("&", urlParameterRegex.findFirstIn(url).fold(s"$url$urlParameterSeparator$urlParameter")(_ => urlParameterRegex.replaceAllIn(url, urlParameter)))
  }
}
