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
  */
class PriceCrawlerAuctionsGraphStage @Inject()(implicit val priceCrawlerUrlService: PriceCrawlerUrlService,
                                               priceCrawlerAuctionService: PriceCrawlerAuctionService,
                                               ec: ExecutionContext)
  extends GraphStage[FlowShape[PriceCrawlerUrlContent, PriceCrawlerAuction]] {

  val in: Inlet[PriceCrawlerUrlContent] = Inlet("PriceCrawlerAuctions.in")
  val out: Outlet[PriceCrawlerAuction] = Outlet("PriceCrawlerAuctions.out")
  override val shape: FlowShape[PriceCrawlerUrlContent, PriceCrawlerAuction] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private var priceCrawlerAuctions = mutable.Queue[PriceCrawlerAuction]()

    implicit val system = ActorSystem("andycot")
    implicit val mat: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(system))

    setHandlers(in, out, new InHandler with OutHandler {
      override def onPush(): Unit = {

        grab(in) match {
          case PriceCrawlerUrlContent(PriceCrawlerUrl(website, url), Some(htmlContent)) =>
            Logger.info(s"Processing WEBSITE $website URL $url w/htmlContent")
            (for {
              auctions <- PriceCrawlerDCP.extractAuctions(website, htmlContent)
              alreadyRecordedAuctions <- priceCrawlerAuctionService.findMany(auctions)
            } yield (auctions, alreadyRecordedAuctions)).onComplete(processHtmlContentCallback(url).invoke)

          case PriceCrawlerUrlContent(PriceCrawlerUrl(website, url), None) =>
            Logger.info(s"Processing WEBSITE $website URL $url w/o htmlContent")
            (for {
              htmlContent <- getHtmlContent(url)
              auctions <- PriceCrawlerDCP.extractAuctions(website, htmlContent)
              alreadyRecordedAuctions <- priceCrawlerAuctionService.findMany(auctions)
            } yield (auctions, alreadyRecordedAuctions)).onComplete(processHtmlContentCallback(url).invoke)
        }
      }

      override def onPull(): Unit = {
        // We pull(in) only if we have emptied the auctions queue, this way we process each url in "sequence"
        if (!pushNextAuction() && !isClosed(in)) {
          pull(in)
        }
      }

      override def onUpstreamFinish(): Unit = {
        Logger.info(s"Upstream finished with ${priceCrawlerAuctions.size} remaining auctions in the queue")
        if (priceCrawlerAuctions.isEmpty) complete(out)
      }
    })

    /**
      *
      * @param url The url from which to grab the html content from
      * @return
      */
    def getHtmlContent(url: String): Future[String] = {
      Logger.info(s"Fetching $url")

      Http().singleRequest(HttpRequest(uri = url)).flatMap {
        case res if res.status.isSuccess =>
          res.entity.dataBytes.runFold(ByteString(""))(_ ++ _).map(_.utf8String)

        case res =>
          Logger.error(s"Error fetching $url status ${res.status}")
          throw new ResourceUnavailable(s"Error fetching $url status ${res.status}")
      }
    }

    /**
      * http://blog.kunicki.org/blog/2016/07/20/implementing-a-custom-akka-streams-graph-stage/
      *
      * @param url The url from which the html content was grabbed from, for debug purposes
      * @return
      */
    private def processHtmlContentCallback(url: String) = getAsyncCallback[Try[(Seq[PriceCrawlerAuction], Seq[PriceCrawlerAuction])]] {
      case Success((auctions, alreadyRecordedAuctions)) if alreadyRecordedAuctions.length == auctions.length && auctions.nonEmpty =>
        Logger.info(s"All the auctions are ALREADY recorded for $url")

        // All auctions are already recorded for the current url, so we don't need to process
        // this url next pages (page n+1, page n+2, page n+3, ...)
        // So we cancel the upstream thus putting the unprocessed urls to the bin.
        cancel(in)

        // To complete the stage in a clean way, we complete the downstream ONLY if there's NO remaining
        // auctions in the queue to push downstream.
        if (priceCrawlerAuctions.isEmpty) complete(out)

      case Success((auctions, alreadyRecordedAuctions)) if alreadyRecordedAuctions.isEmpty =>
        val newAuctions = getNewAuctions(auctions, alreadyRecordedAuctions)
        Logger.info(s"${newAuctions.length} new auctions found for $url")

        // Queue the new auctions
        priceCrawlerAuctions ++= newAuctions

        // Push one auction
        pushNextAuction()

      case Success((auctions, alreadyRecordedAuctions)) =>
        val newAuctions = getNewAuctions(auctions, alreadyRecordedAuctions)
        Logger.info(s"${newAuctions.length} new auctions found for $url")

        // Queue the new auctions
        priceCrawlerAuctions ++= newAuctions

        // Push one auction
        pushNextAuction()

        // Some new auctions were found, so we push this auctions downstream and we cancel the upstream.
        cancel(in)

        // To complete the stage in a clean way, we complete the downstream ONLY if there's NO remaining
        // auctions in the queue to push downstream.
        if (priceCrawlerAuctions.isEmpty) complete(out)

      case Failure(f) =>
        // TODO refactor ???
        Logger.error(s"Error encountered while processing $url", f)
    }

    /**
      * Push the next auction if there's one available in the queue
      * @return true if an auction was pushed
      *         false if no auction was pushed
      */
    def pushNextAuction(): Boolean = {
      if (priceCrawlerAuctions.nonEmpty) {
        push(out, priceCrawlerAuctions.dequeue)
        true
      } else {
        false
      }
    }

    /**
      * Get a list of elements from the "auctions" list that are not in the "alreadyRecordedAuctions" list
      * @param auctions A list of auctions eventually containing new auctions
      * @param alreadyRecordedAuctions The auctions from the "auctions" list that are already recorded in mongodb
      * @return
      */
    def getNewAuctions(auctions: Seq[PriceCrawlerAuction], alreadyRecordedAuctions: Seq[PriceCrawlerAuction]): Seq[PriceCrawlerAuction] = {
      auctions.filterNot(auction => alreadyRecordedAuctions.exists(_.auctionId == auction.auctionId))
    }
  }
}
