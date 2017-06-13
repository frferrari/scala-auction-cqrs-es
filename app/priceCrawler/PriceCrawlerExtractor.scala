package priceCrawler

/**
  * Created by Francois FERRARI on 12/06/2017
  */
trait PriceCrawlerExtractor {
  def extractAuctions(htmlContent: String): List[PriceCrawlerAuction]

  def getPagedUrls(priceCrawlerUrl: PriceCrawlerUrl, htmlContent: String)(implicit priceCrawlerUrlService: PriceCrawlerUrlService): ((PriceCrawlerUrl, String), List[String])

  def getItemPrice(priceWithCurrency: String): PriceCrawlerItemPrice

  def mapToInternalCurrency(externalCurrency: String): Option[String]
}
