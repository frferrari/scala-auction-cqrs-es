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

    //    override def preStart(): Unit = {
    //      Logger.info("PriceCrawlerAuctionsGraphStage.preStart()")
    //      pull(in)
    //    }

    setHandlers(in, out, new InHandler with OutHandler {
      override def onPush(): Unit = {
        val nextUrlContent: PriceCrawlerUrlContent = grab(in)

        Logger.info(s"PriceCrawlerAuctionsGraphStage.onPush() url ${nextUrlContent.url} empty htmlContent=${nextUrlContent.htmlContent.isEmpty}")

        nextUrlContent match {
          // For the first page we always receive the htmlContent so we don't need to read it again
          case PriceCrawlerUrlContent(url, Some(htmlContent)) =>
            (for {
              auctions <- PriceCrawlerDCP.extractAuctions(htmlContent)
              alreadyRecorded <- priceCrawlerAuctionService.findMany(auctions)
            } yield (auctions, alreadyRecorded)).onComplete(processHtmlContentCallback(url).invoke)

          case p@PriceCrawlerUrlContent(url, None) =>
            (for {
              htmlContent <- getHtmlContent(p.url)
              auctions <- PriceCrawlerDCP.extractAuctions(htmlContent)
              alreadyRecorded <- priceCrawlerAuctionService.findMany(auctions)
            } yield (auctions, alreadyRecorded)).onComplete(processHtmlContentCallback(url).invoke)
        }
      }

      override def onPull(): Unit = {
        // Logger.info("PriceCrawlerAuctionsGraphStage.onPull() handler ...")
        // We pullIn only if we have emptied the auctions queue, this way we process each url in "sequence"
        if (!pushNextAuction() && !isClosed(in)) {
          doPullIn("onPull() handler")
        }
      }

      override def onUpstreamFinish(): Unit = {
        Logger.info(s"PriceCrawlerAuctionsGraphStage.onUpstreamFinish() ${priceCrawlerAuctions.size} remaining auctions to push")
        if (priceCrawlerAuctions.nonEmpty)
          emitAllAuctions()
        else
          completeStage()
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
      * http://blog.kunicki.org/blog/2016/07/20/implementing-a-custom-akka-streams-graph-stage/
      *
      * @param url The url from which the html content was grabbed from, for debug purposes
      * @return
      */
    private def processHtmlContentCallback(url: String) = getAsyncCallback[Try[(Seq[PriceCrawlerAuction], Seq[PriceCrawlerAuction])]] {
      case Success((auctions, alreadyRecorded)) if alreadyRecorded.length == auctions.length && auctions.nonEmpty =>
        // TODO refactor with a completeStage() ????
        Logger.info(s"PriceCrawlerAuctionsGraphStage.processHtmlContentCallback($url), all auctions ALREADY recorded, cancel(in) ...")
        cancel(in)
        // pushAllAuctionsAndComplete()

      case Success((auctions, alreadyRecorded)) =>
        val newAuctions = auctions.filterNot(auction => alreadyRecorded.exists(_.auctionId == auction.auctionId))
        Logger.info(s"PriceCrawlerAuctionsGraphStage.processHtmlContentCallback($url), auctions#${auctions.length} already#${alreadyRecorded.length} newAuctions#${newAuctions.length} new auction(s) will be recorded ...")

        // Queue the new auctions
        priceCrawlerAuctions ++= newAuctions

        // Push one auction
        pushNextAuction()

      case Failure(f) =>
        // TODO refactor with a completeStage() ???
        Logger.error(s"processHtmlContentCB.processHtmlContentCallback($url) FAILURE, completeStage() ...", f)
        pushAllAuctionsAndComplete()
    }

    /**
      *
      * @return
      */
    def pushNextAuction(): Boolean = if (priceCrawlerAuctions.nonEmpty) {
      val nextAuction: PriceCrawlerAuction = priceCrawlerAuctions.dequeue
      // Logger.debug(s"PriceCrawlerAuctionsGraphStage.pushNextAuction dequeue ${nextAuction.auctionId} remaining auctions ${priceCrawlerAuctions.size}")
      push(out, nextAuction)
      true
    } else {
      Logger.info(s"PriceCrawlerAuctionsGraphStage.pushNextAuction empty queue")
      false
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

    /**
      *
      */
    def emitAllAuctions(): Unit = {
      Logger.info(s"Remaining auctions in the queue to be emitted: ${priceCrawlerAuctions.size}")
      emitMultiple(out, priceCrawlerAuctions.toIterator)
    }

    def doPullIn(from: String) = {
      Logger.info(s"++++++++++++++++++++++++++++++++ pull(in) from $from")
      pull(in)
    }
  }
}
