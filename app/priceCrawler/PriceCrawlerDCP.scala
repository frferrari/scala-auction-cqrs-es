package priceCrawler

import java.util

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import play.api.Logger

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
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
  val priceCurrencyRegex =
    """([0-9,.]+)(.*)""".r

  val auctionIdRegex =
    """item-([0-9]+)""".r

  override def extractAuctions(website: String, htmlContent: String): Future[Seq[PriceCrawlerAuction]] = Future {
    @tailrec def extractAuction(elementsIterator: util.Iterator[Element], priceCrawlerAuctions: Seq[PriceCrawlerAuction] = Nil): Seq[PriceCrawlerAuction] = {
      elementsIterator.hasNext match {
        case true =>
          val element: Element = elementsIterator.next()
          val imageContainer = element.select("div.item-content > div.image-container")
          val auctionIdRegex(auctionId) = element.attr("id")

          if (imageContainer.select("a.img-view").hasClass("default-thumb")) {
            Logger.info(s"PriceCrawlerDCP.extractAuction auction $auctionId has no picture, skipping ...")
            extractAuction(elementsIterator, priceCrawlerAuctions)
          } else {

            val auctionId = imageContainer.select("a.img-view").attr("data-item-id").trim
            val largeUrl = element.select("a.img-view").attr("href").trim
            //
            val imgThumbElement = imageContainer.select("img.image-thumb")
            val auctionTitle = imgThumbElement.attr("alt").trim
            val thumbUrl = imgThumbElement.attr("data-original").trim

            //
            val itemFooterElement = element.select("div.item-content > div.item-footer")
            val auctionUrl = itemFooterElement.select("a.item-link").attr("href").trim
            val itemPrice = itemFooterElement.select(".item-price").text().trim

            if ( auctionId.length > 0 &&
              auctionUrl.length > 0 &&
              auctionTitle.length > 0 &&
              thumbUrl.length > 0 &&
              largeUrl.length > 0
            ) {
              getItemPrice(itemPrice) match {
                case Success(priceCrawlerItemPrice) =>
                  extractAuction(elementsIterator, priceCrawlerAuctions :+ PriceCrawlerAuction(auctionId, website, auctionUrl, auctionTitle, thumbUrl, largeUrl, priceCrawlerItemPrice))

                case Failure(_) =>
                  extractAuction(elementsIterator, priceCrawlerAuctions)
              }
            } else {
              Logger.info(s"PriceCrawlerDCP.extractAuction auction $auctionId is missing some informations, skipping ...")
              extractAuction(elementsIterator, priceCrawlerAuctions)
            }
          }

        case false =>
          priceCrawlerAuctions
      }
    }

    extractAuction(Jsoup.parse(htmlContent).select(".item-gallery").iterator())
  }

  /**
    *
    * @param priceCrawlerUrl
    * @param priceCrawlerWebsites
    * @param htmlContent
    * @param priceCrawlerUrlService
    * @return
    */
  override def getPagedUrls(priceCrawlerUrl: PriceCrawlerUrl, priceCrawlerWebsites: Seq[PriceCrawlerWebsite], htmlContent: String)
                           (implicit priceCrawlerUrlService: PriceCrawlerUrlService): Seq[String] = {
    val pageNumberRegex = """.*<a class="pag-number.*" href=".*">([0-9]+)</a>.*""".r

    pageNumberRegex.findAllIn(htmlContent).matchData.flatMap(_.subgroups).toList.lastOption match {
      case Some(lastPageNumber) =>
        priceCrawlerUrlService.generateAllUrls(priceCrawlerUrl, priceCrawlerWebsites, lastPageNumber.toInt)

      case None =>
        // Case when there's only one page of auctions for this category
        priceCrawlerUrlService.generateAllUrls(priceCrawlerUrl, priceCrawlerWebsites, 1)
    }
  }

  /**
    *
    * @param priceWithCurrency
    * @return
    */
  override def getItemPrice(priceWithCurrency: String): Try[PriceCrawlerItemPrice] = Try {

    val priceCurrencyRegex(price, externalCurrency) = priceWithCurrency

    mapToInternalCurrency(externalCurrency) match {
      case Some(internalCurrency) =>
        // An external price is a string like "120,00" or "4 950,00"
        PriceCrawlerItemPrice(BigDecimal(price.replace(",", ".").replace(" ", "")), internalCurrency)

      case _ =>
        Logger.error(s"PriceCrawlerDCP.getItemPrice Couldn't parse currency $externalCurrency")
        throw new Exception(s"PriceCrawlerDCP.getItemPrice Couldn't parse currency $externalCurrency")
    }
  }

  /**
    *
    * @param externalCurrency
    * @return
    */
  override def mapToInternalCurrency(externalCurrency: String): Option[String] = currencyMap.get(externalCurrency.trim)
}
