package priceCrawler

/**
  * Created by Francois FERRARI on 12/06/2017
  */
trait PriceCrawlerExtractor {
  def getAuctionUrls(htmlContent: String): List[PriceCrawlerAuction]
}
