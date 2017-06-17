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
    private var priceCrawlerAuctions = mutable.Queue[PriceCrawlerAuction]()

    implicit val system = ActorSystem("andycot")
    implicit val mat: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(system))
    //
    //    override def preStart(): Unit = {
    //      Logger.info("PriceCrawlerAuctionsGraphStage.preStart()")
    //      pull(in)
    //    }

    setHandlers(in, out, new InHandler with OutHandler {
      override def onPush(): Unit = {
        val nextUrlContent: PriceCrawlerUrlContent = grab(in)

        Logger.info(s"PriceCrawlerAuctionsGraphStage.onPush() nextUrlContent ${nextUrlContent.url} htmlContent.isEmpty=${nextUrlContent.htmlContent.isEmpty}")

        nextUrlContent match {
          case PriceCrawlerUrlContent(url, Some(htmlContent)) =>
            if (processHtmlContent(htmlContent)) completeStage()
            pull(in)

          case p@PriceCrawlerUrlContent(url, None) =>
            /*
             * We don't pull(in) here because the getHtmlContent is asynchronous.
             * We want this future to complete before reading the next url to
             * process and only then we can pull(in), otherwise we would have all
             * this futures running concurrently.
             * That's why the pull(in) is madefrom inside the getHtmlContentCB where
             * we are sure that the future is finished.
             */
            getHtmlContent(p.url).onComplete(getHtmlContentCB(p.url).invoke)
        }
      }

      override def onPull(): Unit = {
        pushNextAuction()
        pull(in)
      }

      override def onUpstreamFinish(): Unit = {
        Logger.info("PriceCrawlerAuctionsGraphStage.onUpstreamFinish()")

        pushAllAuctions()
        // if (priceCrawlerAuctions.isEmpty) completeStage()
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
        pull(in)
        if (processHtmlContent(htmlContent)) completeStage()

      case Failure(f) =>
        Logger.error(s"Enable to get the htmlContent for url $url", f)
        pull(in)
        completeStage()
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
        priceCrawlerAuctions ++= alreadyRecorded
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
    def pushAllAuctions(): Unit = priceCrawlerAuctions.foreach(push(out, _))
  }
}
