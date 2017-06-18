package priceCrawler

/**
  * Created by Francois FERRARI on 12/06/2017
  */
case class PriceCrawlerWebsite(name: String,
                               baseUrl: String,
                               defaultUrlParameters: Seq[String]
                               // created_at: Instant
                              )
