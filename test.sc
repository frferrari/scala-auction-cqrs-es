
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, Graph, SourceShape}
import akka.{Done, NotUsed}
import priceCrawler._

import scala.concurrent.Future

lazy implicit val materializer = ActorMaterializer()

val mongoService = new PriceCrawlerUrlService

val sourceGraph : Graph[SourceShape[PriceCrawlerUrl], NotUsed] = new PriceCrawlerUrlGraphStage(mongoService)

val mySource: Source[PriceCrawlerUrl, NotUsed] = Source.fromGraph(sourceGraph)

val r1: Future[Done] = mySource.take(1).runForeach(println)
