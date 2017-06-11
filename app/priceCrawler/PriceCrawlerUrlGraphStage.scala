package priceCrawler

import java.time.Instant
import javax.inject.Inject

import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}
import akka.stream.{Attributes, Outlet, SourceShape}
import play.api.Logger

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

/**
  * Created by Francois FERRARI on 10/06/2017
  *
  * This code allows to produce an infinite stream of URLs to grab prices from.
  * We read the url list from a database. The url list is not supposed to change frequently
  * anyway an update mechanism is in place.
  *
  * Stopping the infinite stream could be done by adding a call to a service that would return
  * a flag to tell if a stop if requested (code to be provided)
  *
  * Some help about how to use getAsyncCallback was found here:
  * http://doc.akka.io/docs/akka/current/scala/stream/stream-customize.html
  * http://doc.akka.io/docs/akka/current/scala/stream/stream-customize.html#custom-processing-with-graphstage
  *
  */
class PriceCrawlerUrlGraphStage @Inject()(implicit priceCrawlerUrlService: PriceCrawlerUrlService, ec: ExecutionContext)
  extends GraphStage[SourceShape[PriceCrawlerUrl]] {

  val elapsedSecondsBetweenUpdates = 300

  val out: Outlet[PriceCrawlerUrl] = Outlet("PriceCrawlerUrlGraphStage")
  override val shape: SourceShape[PriceCrawlerUrl] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private var priceCrawlerUrls = Seq.empty[PriceCrawlerUrl]
    private var index = -1
    private var lastUpdate = Instant.now().minusSeconds(elapsedSecondsBetweenUpdates*2)

    /*
     * http://tech.measurence.com/2016/06/01/a-dive-into-akka-streams.html
     * https://groups.google.com/forum/#!msg/akka-user/fBkWg4gSwEI/uiC2j1U7AAAJ;context-place=msg/akka-user/XQo2G7_mTcQ/8JKrM_TnDgAJ
     */
    private def safePushCB(withUpdate: Boolean) = getAsyncCallback[Try[Seq[PriceCrawlerUrl]]] {
      case Success(urls) =>
        if (withUpdate) {
          lastUpdate = Instant.now
          priceCrawlerUrls = urls
        }
        pushNextUrl

      case Failure(f) =>
        Logger.error(s"Enable to update the priceCrawlerUrls", f)
        completeStage()
    }

    private def pushNextUrl = {
      val nextUrl = getNextUrl(priceCrawlerUrls)
      push(out, nextUrl)
    }

    private def getNextUrl(urls: Seq[PriceCrawlerUrl]) = {
      index = if (index >= urls.length - 1) 0 else index + 1
      val nextUrl = urls(index)
      nextUrl
    }

    setHandler(out, new OutHandler {
      override def onPull(): Unit = {
        // Check if an update of the url list is needed
        if (Instant.now.getEpochSecond - lastUpdate.getEpochSecond > elapsedSecondsBetweenUpdates) {
          priceCrawlerUrlService.findPriceCrawlerUrls.onComplete(safePushCB(true).invoke)
        } else {
          // Not optimal: Future.fromTry(Try(priceCrawlerUrls)).onComplete(safePushCB(false).invoke)
          pushNextUrl
        }
      }
    })
  }
}
