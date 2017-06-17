import java.util

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import play.api.Logger
import priceCrawler.{PriceCrawlerAuction, PriceCrawlerItemPrice}

import scala.collection.immutable.Seq


val doc = Jsoup.connect("https://www.delcampe.fr/fr/collections/search?term=&categories%5B%5D=1245&excluded_terms=&country=FR&search_mode=all&show_type=all&display_ongoing=sold&started_days=&ended_hours=&all-payment-methods=on&min_price=&max_price=&order=sale_start_datetime&seller_localisation=world&blacklisted_sellers_included=0").get()

val auctions: util.Iterator[Element] = doc.select(".item-gallery").iterator()

extract(auctions, Nil)

def extract(elementsIterator: util.Iterator[Element], priceCrawlerAuctions: Seq[PriceCrawlerAuction]): Seq[PriceCrawlerAuction] = {
  elementsIterator.hasNext match {
    case true =>
      val element: Element = elementsIterator.next()
      val imageContainer= element.select("div.item-content > div.image-container")
      val auctionId = imageContainer.select("a.img-view").attr("data-item-id")

      if (imageContainer.select("a.img-view").hasClass("default-thumb")) {

        Logger.info(s"extract: auction $auctionId has no picture, skipping ...")
        extract(elementsIterator, priceCrawlerAuctions)
      } else {
        val auctionId = imageContainer.select("a.img-view").attr("data-item-id")
        val largeUrl = element.select("a.img-view").attr("href")
        //
        val imgThumbElement = imageContainer.select("img.image-thumb")
        val auctionTitle = imgThumbElement.attr("alt")
        val thumbUrl = imgThumbElement.attr("data-original")

        //
        val itemFooterElement = element.select("div.item-content > div.item-footer")
        val auctionUrl = itemFooterElement.select("a.item-link").attr("href")
        val itemPrice = itemFooterElement.select(".item-price").text()
        val priceCrawlerItemPrice = PriceCrawlerItemPrice(0, "")

        getItemPrice()

        extract(elementsIterator, priceCrawlerAuctions :+ PriceCrawlerAuction(auctionId, auctionUrl, auctionTitle, thumbUrl, largeUrl, priceCrawlerItemPrice))
      }

    case false =>
      priceCrawlerAuctions
  }
}

