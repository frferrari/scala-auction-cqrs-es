package priceCrawler

import javax.inject.Inject

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream.stage._
import akka.stream.{ActorMaterializerSettings, _}
import akka.util.ByteString
import play.api.Logger

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
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
class PriceCrawlerAuctionsGraphStage @Inject()(implicit val priceCrawlerUrlService: PriceCrawlerUrlService,
                                               ec: ExecutionContext)
  extends GraphStage[FlowShape[PriceCrawlerUrlContent, PriceCrawlerAuction]] {

  val in: Inlet[PriceCrawlerUrlContent] = Inlet("PriceCrawlerAuctions.in")
  val out: Outlet[PriceCrawlerAuction] = Outlet("PriceCrawlerAuctions.out")
  override val shape = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private var priceCrawlerUrlContents = mutable.Queue[PriceCrawlerAuction]()

    implicit val system = ActorSystem("andycot")
    implicit val mat: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(system))
//
//    override def preStart(): Unit = {
//      Logger.info("PriceCrawlerAuctionsGraphStage.preStart()")
//      pull(in)
//    }

    setHandlers(in, out, new InHandler with OutHandler {
      override def onPush(): Unit = {
        val nextElement: PriceCrawlerUrlContent = grab(in)
        pull(in)

        Logger.info(s"PriceCrawlerAuctionsGraphStage.onPush() nextElement ${nextElement.url} htmlContent.isEmpty=${nextElement.htmlContent.isEmpty}")

        nextElement match {
          case PriceCrawlerUrlContent(url, Some(htmlContent)) =>
            if (processHtmlContent(htmlContent)) completeStage()

          case p@PriceCrawlerUrlContent(url, None) =>
            getHtmlContent(p.url).onComplete(getHtmlContentCB(p.url).invoke)
        }
      }

      override def onPull(): Unit = {
        if (priceCrawlerUrlContents.nonEmpty) {
          val nextElement: PriceCrawlerAuction = priceCrawlerUrlContents.dequeue
          Logger.info(s"PriceCrawlerAuctionsGraphStage.onPull() dequeue $nextElement")
          push(out, nextElement)
        } else {
          Logger.info(s"PriceCrawlerAuctionsGraphStage.onPull() empty queue")
        }
        pull(in)
      }

      override def onUpstreamFinish(): Unit = {
        // TODO ??? Anything else to do ???
        Logger.info("PriceCrawlerAuctionsGraphStage.onUpstreamFinish()")
        if (priceCrawlerUrlContents.isEmpty) completeStage()
      }
    })

    /**
      * Callback called when the html content has been grabbed
      *
      * http://blog.kunicki.org/blog/2016/07/20/implementing-a-custom-akka-streams-graph-stage/
      *
      * @param url The url from which the html content was grabbed from, for debug purposes
      * @return
      */
    private def getHtmlContentCB(url: String) = getAsyncCallback[Try[String]] {
      case Success(htmlContent) =>
        Logger.info("PriceCrawlerAuctionsGraphStage.getHtmlContentCB")
        if (processHtmlContent(htmlContent)) completeStage()

      case Failure(f) =>
        Logger.error(s"Enable to get the htmlContent for url $url", f)
        completeStage()

      case x =>
        Logger.error(s"getHtmlContentCB ............... something else $x")
    }

    /**
      * Extract all the informations about auctions found in an html page and stops the graphStage if all
      * the auctions are already processed OR no auctions were found.
      *
      * @param htmlContent The html page content from which to extract the auctions informations
      * @return
      */
    def processHtmlContent(htmlContent: String): Boolean = {
      // TODO PriceCrawlerDCP depends on the website we are crawling
      val auctions: List[PriceCrawlerAuction] = PriceCrawlerDCP.extractAuctions(htmlContent)
      val alreadyRecorded = priceCrawlerUrlService.auctionsAlreadyRecorded(auctions)

      Logger.info(s"PriceCrawlerAuctionsGraphStage.processHtmlContent auctionIds=${auctions.map(_.auctionId)}")

      if (alreadyRecorded.length == auctions.length && auctions.nonEmpty) {
        true
      } else {
        priceCrawlerUrlContents ++= alreadyRecorded
        false
      }
    }

    /**
      *
      * @param url The url from which to grab the html content from
      * @return
      */
    def getHtmlContent(url: String): Future[String] = {
      Logger.info(s"PriceCrawlerAuctionsGraphStage.getHtmlContent($url)")

      Http().singleRequest(HttpRequest(uri = url)).flatMap {
        case res if res.status.isSuccess =>
          Logger.info(s"PriceCrawlerAuctionsGraphStage.getHtmlContent Success $url")
          res.entity.dataBytes.runFold(ByteString(""))(_ ++ _).map(_.utf8String)

        case res =>
          Logger.error(s"Enable to access url $url error ${res.status}")
          throw new ResourceUnavailable(s"Unable to access url $url error ${res.status}")
      }
    }
  }
}
