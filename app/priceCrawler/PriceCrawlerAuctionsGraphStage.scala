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
import scala.util.control.NonFatal
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
                                               priceCrawlerAuctionService: PriceCrawlerAuctionService,
                                               ec: ExecutionContext)
  extends GraphStage[FlowShape[PriceCrawlerUrlContent, PriceCrawlerAuction]] {

  val in: Inlet[PriceCrawlerUrlContent] = Inlet("PriceCrawlerAuctions.in")
  val out: Outlet[PriceCrawlerAuction] = Outlet("PriceCrawlerAuctions.out")
  override val shape = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private var priceCrawlerAuctions = mutable.Queue[PriceCrawlerAuction]()

    implicit val system = ActorSystem("andycot")
    implicit val mat: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(system))

    override def preStart(): Unit = {
      Logger.info("PriceCrawlerAuctionsGraphStage.preStart()")
      pull(in)
    }

    setHandlers(in, out, new InHandler with OutHandler {
      override def onPush(): Unit = {
        val nextUrlContent: PriceCrawlerUrlContent = grab(in)

        Logger.info(s"PriceCrawlerAuctionsGraphStage.onPush() nextUrlContent ${nextUrlContent.url} htmlContent.isEmpty=${nextUrlContent.htmlContent.isEmpty}")

        nextUrlContent match {
          case PriceCrawlerUrlContent(url, Some(htmlContent)) =>
            processHtmlContent(url, htmlContent)

          case p@PriceCrawlerUrlContent(url, None) =>
            getHtmlContent(p.url).map(processHtmlContent(p.url, _))
        }
      }

      override def onPull(): Unit = {
        Logger.info("PriceCrawlerAuctionsGraphStage.onPull ...")
        pushNextAuction()
        // pull(in)
      }

      override def onUpstreamFinish(): Unit = {
        Logger.info("PriceCrawlerAuctionsGraphStage.onUpstreamFinish()")
        pushAllAuctionsAndComplete()
      }
    })

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
        processHtmlContent(url, htmlContent)

      case Failure(f) =>
        Logger.error(s"PriceCrawlerAuctionsGraphStage.getHtmlContentCB($url) Enable to get the htmlContent", f)
        completeStage()
    }

    /**
      * Extract all the informations about auctions found in an html page and stops the graphStage if all
      * the auctions are already processed OR no auctions were found.
      *
      * @param htmlContent The html page content from which to extract the auctions informations
      * @return
      */
    def processHtmlContent(url: String, htmlContent: String): Unit = {
      // TODO PriceCrawlerDCP depends on the website we are crawling
      PriceCrawlerDCP.extractAuctions(htmlContent).map { auctions =>
        priceCrawlerAuctionService.findMany(auctions).onComplete(processHtmlContentCB(url, auctions).invoke)
      }.recover {
        case NonFatal(e) =>
          Logger.error("PriceCrawlerAuctionsGraphStage.processHtmlContent Error encountered", e)
      }
    }

    private def processHtmlContentCB(url: String, auctions: Seq[PriceCrawlerAuction]) = getAsyncCallback[Try[Seq[PriceCrawlerAuction]]] {
      case Success(alreadyRecorded) if alreadyRecorded.length == auctions.length && auctions.nonEmpty =>
        Logger.info(s"PriceCrawlerAuctionsGraphStage.processHtmlContentCB($url), all auctions ALREADY recorded, completeStage() ...")
        pushAllAuctionsAndComplete()

      case Success(alreadyRecorded) =>
        val newAuctions = auctions.diff(alreadyRecorded)
        Logger.info(s"PriceCrawlerAuctionsGraphStage.processHtmlContentCB($url), ${newAuctions.length} new auction(s) will be recorded ...")

        priceCrawlerAuctions ++= newAuctions

        priceCrawlerAuctionService
          .createMany(newAuctions)
          .recover {
            case NonFatal(e) =>
              Logger.error("PriceCrawlerAuctionsGraphStage.processHtmlContentCB persistence error", e)
          }

        pull(in)

      case Failure(f) =>
        Logger.error(s"processHtmlContentCB.processHtmlContentCB($url) FAILURE, completeStage() ...", f)
        pushAllAuctionsAndComplete()
    }

    /**
      *
      */
    def pushNextAuction(): Unit = if (priceCrawlerAuctions.nonEmpty) {
      val nextAuction: PriceCrawlerAuction = priceCrawlerAuctions.dequeue
      Logger.info(s"PriceCrawlerAuctionsGraphStage.pushNextAuction dequeue $nextAuction")
      push(out, nextAuction)
    } else {
      Logger.info(s"PriceCrawlerAuctionsGraphStage.pushNextAuction empty queue")
    }

    /**
      *
      */
    def pushAllAuctionsAndComplete(): Unit = {
      Logger.info(s"Remaining auctions in the queue to be pushed: ${priceCrawlerAuctions.size}")
      priceCrawlerAuctions.foreach(push(out, _))
      cancel(in)
      // completeStage()
    }
  }
}
