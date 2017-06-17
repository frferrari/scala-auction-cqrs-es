package priceCrawler

import scala.util.Try

/**
  * Created by Francois FERRARI on 12/06/2017
  */
trait PriceCrawlerExtractor {
  def extractAuctions(htmlContent: String): Try[Seq[PriceCrawlerAuction]]

  def getPagedUrls(priceCrawlerUrl: PriceCrawlerUrl, htmlContent: String)(implicit priceCrawlerUrlService: PriceCrawlerUrlService): List[String]

  def getItemPrice(priceWithCurrency: String): Try[PriceCrawlerItemPrice]

  def mapToInternalCurrency(externalCurrency: String): Option[String]
}
