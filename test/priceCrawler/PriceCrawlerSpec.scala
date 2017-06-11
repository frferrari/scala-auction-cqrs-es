package priceCrawler

import org.scalatest.{Matchers, WordSpecLike}
import play.api.inject.guice.GuiceApplicationBuilder

/**
  * Created by Francois FERRARI on 17/05/2017
  */
class PriceCrawlerSpec
  extends WordSpecLike
  with Matchers {

  val app = new GuiceApplicationBuilder().build()
  val injector = app.injector
  val priceCrawlerUrlService = injector.instanceOf(classOf[PriceCrawlerUrlService])

  "priceCrawler" should {
    "successfully generate a list of urls given a baseUrl with a ?page=1 string" in {
      val expectedUrls: List[String] = List(
        "http://www.andycot.fr?page=1",
        "http://www.andycot.fr?page=2",
        "http://www.andycot.fr?page=3",
        "http://www.andycot.fr?page=4"
      )

      priceCrawlerUrlService.generateAllUrls(PriceCrawlerUrl("http://www.andycot.fr?page=1"), 4) shouldBe(expectedUrls)
    }

    "successfully generate a list of urls given a baseUrl with a &page=1 string" in {
      val expectedUrls: List[String] = List(
        "http://www.andycot.fr?sort=1&page=1",
        "http://www.andycot.fr?sort=1&page=2",
        "http://www.andycot.fr?sort=1&page=3",
        "http://www.andycot.fr?sort=1&page=4"
      )

      priceCrawlerUrlService.generateAllUrls(PriceCrawlerUrl("http://www.andycot.fr?sort=1&page=1"), 4) shouldBe(expectedUrls)
    }
  }
}
