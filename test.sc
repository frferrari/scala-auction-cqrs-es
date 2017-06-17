import org.mongodb.scala._
import org.mongodb.scala.model.Filters._
import priceCrawler.PriceCrawlerAuctionService

import scala.concurrent.Await
import scala.concurrent.duration.Duration

val priceCrawlerAuctionService = new PriceCrawlerAuctionService

//val auctions = List(
//  PriceCrawlerAuction("456275443", "", "", "", "", PriceCrawlerItemPrice("", "")),
//  PriceCrawlerAuction("2", "", "", "", "", PriceCrawlerItemPrice("", ""))
//)

val auctionIds = List("456275443", "456153648")

val r = Await.result(
  priceCrawlerAuctionService
    .collection
    .find(
      in("auctionId", auctionIds:_*)
    ).toFuture(), Duration.Inf)


//val r = Await.result(
//  priceCrawlerAuctionService
//    .collection
//    // .find[PriceCrawlerAuction](equal("auctionId", "456275443"))
//    .find().first()
//    .toFuture()
//  , Duration.Inf
//)
//
//priceCrawlerAuctionService.findMany(auctions).onComplete {
//  case Success(auctions) =>
//    auctions.foreach(auction => println(s"===> $auction"))
//
//  case Failure(e) =>
//    println("error ................")
//}


