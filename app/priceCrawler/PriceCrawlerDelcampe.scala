package priceCrawler

import play.api.Logger

import scala.util.matching.Regex

/**
  * Created by Francois FERRARI on 12/06/2017
  */

object PriceCrawlerDelcampe extends PriceCrawlerExtractor {
  /**
    *
    * @param htmlContent
    * @return
    */
  override def getAuctionUrls(htmlContent: String): List[PriceCrawlerAuction] = {
    /*
     *
     * Current html for thumbnails
     *
     * <img alt="ALAND Multiculturalisme 1v 2017  Neuf ** MNH - Aland" class="image-thumb" src="https://images-01.delcampe-static.net/img_thumb/auction/000/454/203/045_001.jpg?v=0">
     *
     */
    val thumbRegex: Regex =
      """<img alt=".*" class="image-thumb" src="(.*)">""".r
    // The following regex is used to parse the src part of the thumbnails html (something like this .../auction/000/454/203/045_001.jpg)
    val auctionIdRegex =
      """.*\/auction\/([0-9]{3})\/([0-9]{3})\/([0-9]{3})\/([0-9]{3})_.*\.jpg""".r

    /*
     *
     * Current html for auction link
     *
     * <a href="/fr/collections/timbres/aland/aland-multiculturalisme-1v-2017-neuf-mnh-454203045.html" title="ALAND Multiculturalisme 1v 2017  Neuf ** MNH" class="item-link" target="_blank">
     *
     */
    val auctionLinkRegex: Regex =
      """<a href="(.*)" title=".*" class="item-link" target="_blank">""".r

    val auctions: List[String] = auctionLinkRegex.findAllIn(htmlContent).matchData.flatMap(_.subgroups).toList
    val thumbnails: List[String] = thumbRegex.findAllIn(htmlContent).matchData.flatMap(_.subgroups).toList
    val auctionIds: List[String] = thumbnails.flatMap(thumbnail => auctionIdRegex.findAllIn(thumbnail).matchData.map(_.subgroups.mkString)).toList

    thumbnails.length == auctions.length && auctions.length == auctionIds.length match {
      case true =>
        (auctionIds, auctions, thumbnails).zipped.toList.map(PriceCrawlerAuction.tupled)

      case false =>
        Logger.error(s"Enable to parse thumbnails (${thumbnails.length}) or auctions (${auctions.length}) or auctionIds (${auctionIds.length})")
        Nil
    }
  }

  /**
    *
    * @param priceCrawlerUrl
    * @param htmlContent
    * @param priceCrawlerUrlService
    * @return
    */
  override def getPagedUrls(priceCrawlerUrl: PriceCrawlerUrl, htmlContent: String)(implicit priceCrawlerUrlService: PriceCrawlerUrlService): ((PriceCrawlerUrl, String), List[String]) = {
    val pageNumberRegex = """.*<a class="pag-number.*" href=".*">([0-9]+)</a>.*""".r

    pageNumberRegex.findAllIn(htmlContent).matchData.flatMap(_.subgroups).toList.lastOption match {
      case Some(lastPageNumber) =>
        (priceCrawlerUrl, htmlContent) -> priceCrawlerUrlService.generateAllUrls(priceCrawlerUrl, lastPageNumber.toInt)

      case None =>
        Logger.error(s"Enable to parse the last page number from website ${priceCrawlerUrl.website} url ${priceCrawlerUrl.url}")
        throw new IllegalArgumentException(s"Enable to parse the last page number from url $priceCrawlerUrl")
    }
  }
}
