package priceCrawler

import play.api.Logger

import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

/**
  * Created by Francois FERRARI on 12/06/2017
  */

object PriceCrawlerDCP extends PriceCrawlerExtractor {
  /*
   * See UTF-8 tables
   *
   * http://utf8-chartable.de/unicode-utf8-table.pl?start=8320&number=128&names=-
   * http://www.utf8-chartable.de/
   */
  val currencyMap = Map(
    "\u00a0\u20ac" -> "EUR", // 00a0 = space    20ac = €
    "\u00a0\u20a4" -> "GBP"
  )

  // Matches a price string like this one "6,00 €"
  val priceCurrencyRegex = """([0-9,.]+)(.*)""".r

  /**
    *
    * @param htmlContent
    * @return
    */
  override def extractAuctions(htmlContent: String): List[PriceCrawlerAuction] = {
    /*
     *
     * Current html for thumbnails
     *
     * <img alt="ALAND Multiculturalisme 1v 2017  Neuf ** MNH - Aland" class="image-thumb" src="https://images-01.static.net/img_thumb/auction/000/454/203/045_001.jpg?v=0">
     *
     */
    val thumbRegex: Regex =
      """<img alt=".*" class="image-thumb" src="(.*)">""".r

    /*
     * The following regex is used to parse the src part of the thumbnails html (something like this .../auction/000/454/203/045_001.jpg)
     */
    val auctionIdRegex =
      """.*\/auction\/([0-9]{3})\/([0-9]{3})\/([0-9]{3})\/([0-9]{3})_.*\.jpg""".r

    /*
     * The following regex is used to parse the item prices
     * (?s) enables multiline parsing as the typical html where we can find the item-prices is as follows:
     *
     * div class="item-price">
     *    <strong>65,00 €</strong>
     * </div>
     */
    val itemPriceRegex =
      """(?s)<div class="item-price">[^<]*<strong>([^<]+)</strong>""".r
      // """(?s).*<div class="item-price">.*<strong>([^<]+)</strong>.*</div>.*""".r

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
    val itemPrices: List[String] = itemPriceRegex.findAllIn(htmlContent).matchData.flatMap(_.subgroups).toList

    if (thumbnails.length == auctions.length && thumbnails.length == auctionIds.length && thumbnails.length == itemPrices.length) {
      List(auctionIds, auctions, thumbnails, itemPrices).transpose.toList.collect {
        case info if info.length == 4 =>
          PriceCrawlerAuction(info.head, info(1), info(2), getItemPrice(info.last))
      }
    } else {
      Logger.error(s"Enable to parse thumbnails (${thumbnails.length}) or auctions (${auctions.length}) or auctionIds (${auctionIds.length}) or itemPrices (${itemPrices.length})")
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

  override def getItemPrice(priceWithCurrency: String): PriceCrawlerItemPrice = Try {
    val priceCurrencyRegex(price, externalCurrency) = priceWithCurrency
    price -> externalCurrency
  } match {
    case Success((price, externalCurrency)) =>
      mapToInternalCurrency(externalCurrency) match {
        case Some(internalCurrency) =>
          // An external price is a string like "4 950,00"
          PriceCrawlerItemPrice(BigDecimal(price.replace(",", ".").replace(" ", "")), internalCurrency)

        case _ =>
          Logger.error(s"PriceCrawlerItemPrice Couldn't parse currency $externalCurrency")
          throw new Exception(s"PriceCrawlerItemPrice Couldn't parse currency $externalCurrency")
      }

    case Failure(f) =>
      Logger.error(s"PriceCrawlerItemPrice Couldn't parse $priceWithCurrency")
      throw new Exception(s"PriceCrawlerItemPrice Couldn't parse $priceWithCurrency")
  }

  override def mapToInternalCurrency(externalCurrency: String): Option[String] = currencyMap.get(externalCurrency.trim)
}
