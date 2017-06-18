package priceCrawler

import scala.concurrent.Future
import scala.util.Try

/**
  * Created by Francois FERRARI on 12/06/2017
  */
trait PriceCrawlerExtractor {
  def extractAuctions(website: String, htmlContent: String): Future[Seq[PriceCrawlerAuction]]

  def getPagedUrls(priceCrawlerUrl: PriceCrawlerUrl, htmlContent: String)(implicit priceCrawlerUrlService: PriceCrawlerUrlService): List[String]

  def getItemPrice(priceWithCurrency: String): Try[PriceCrawlerItemPrice]

  def mapToInternalCurrency(externalCurrency: String): Option[String]
}
