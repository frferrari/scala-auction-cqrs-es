package priceCrawler

import javax.inject.Inject

import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}
import akka.stream.{Attributes, Outlet, SourceShape}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

/**
  * Created by Francois FERRARI on 10/06/2017
  *
  * http://doc.akka.io/docs/akka/current/scala/stream/stream-customize.html
  * http://doc.akka.io/docs/akka/current/scala/stream/stream-customize.html#custom-processing-with-graphstage
  *
  */
class PriceCrawlerUrlSource @Inject()(implicit priceCrawlerUrlService: PriceCrawlerUrlService, ec: ExecutionContext) extends GraphStage[SourceShape[PriceCrawlerUrl]] {
  // Define the (sole) output port of this stage
  val out: Outlet[PriceCrawlerUrl] = Outlet("PriceCrawlerUrlSource")
  // Define the shape of this stage, which is SourceShape with the port we defined above
  override val shape: SourceShape[PriceCrawlerUrl] = SourceShape(out)

  // This is where the actual (possibly stateful) logic will live
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    // All state MUST be inside the GraphStageLogic,
    // never inside the enclosing GraphStage.
    // This state is safe to access and modify from all the
    // callbacks that are provided by GraphStageLogic and the
    // registered handlers.
    private var priceCrawlerUrlsF = Await.result(priceCrawlerUrlService.findPriceCrawlerUrls, 2.seconds)
    private var index = 0

    setHandler(out, new OutHandler {
      override def onPull(): Unit = {
        val r = priceCrawlerUrlsF(index)
        println(s"====> priceCrawlUrls=$r")
        push(out, priceCrawlerUrlsF(index))
        index = if (index >= priceCrawlerUrlsF.length - 1) 0 else index + 1

        //        priceCrawlerUrlsF.onComplete {
        //          case Success(priceCrawlerUrls) if priceCrawlerUrls.nonEmpty =>
        //            val r = priceCrawlerUrls(index)
        //            println(s"====> priceCrawlUrls=$r")
        //            push(out, priceCrawlerUrls(index))
        //            index = if (index >= priceCrawlerUrls.length-1) 0 else index+1
        //
        //          case Success(priceCrawlerUrls) =>
        //            Logger.error("====> Empty collection")
        //
        //          case Failure(f) =>
        //            Logger.error(s"====> Failure in onPull", f)
        //        }
      }
    })
  }
}
