package controllers

import java.time.{Instant, LocalDate}
import java.util.UUID
import javax.inject.{Inject, Named, Singleton}

import actors.auction.AuctionActor
import actors.user.UserActor
import actors.userUnicity.UserUnicityActor
import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream._
import akka.stream.scaladsl.{Flow, Framing, Source}
import akka.util.ByteString
import cqrs.UsersBid
import cqrs.commands._
import models._
import play.api.Logger
import play.api.mvc.{Action, Controller}
import priceCrawler._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

/**
  * Created by Francois FERRARI on 31/05/2017
  */
@Singleton
class AuctionController @Inject()(@Named(UserUnicityActor.name) userUnicityActorRef: ActorRef)
                                 (implicit priceCrawlerUrlService: PriceCrawlerUrlService,
                                  priceCrawlerAuctionService: PriceCrawlerAuctionService,
                                  ec: ExecutionContext)
  extends Controller {

  type BasePriceCrawlerUrlWithHtmlContent = (PriceCrawlerUrl, String)

  var x = 1

  def test = Action { implicit request =>

    implicit val actorSystem = ActorSystem("AuctionSystem")

    val sellerA = User(
      userId = UUID.randomUUID(),
      emailAddress = EmailAddress("bob.eponge@cartoon.universe"),
      password = "",
      isSuperAdmin = false,
      receivesNewsletter = false,
      receivesRenewals = false,
      currency = "EUR",
      nickName = "sellerA",
      lastName = "Eponge",
      firstName = "Bob",
      lang = "fr",
      avatar = "",
      dateOfBirth = LocalDate.of(2000, 1, 1),
      phone = "",
      mobile = "",
      fax = "",
      description = "",
      sendingCountry = "",
      invoiceName = "",
      invoiceAddress1 = "",
      invoiceAddress2 = "",
      invoiceZipCode = "",
      invoiceCity = "",
      invoiceCountry = "",
      vatIntra = "",
      holidayStartAt = None,
      holidayEndAt = None,
      holidayHideId = UUID.randomUUID(),
      bidIncrement = 0.10,
      listedTimeId = UUID.randomUUID(),
      slug = "",
      watchedAuctions = Nil,
      activatedAt = Some(Instant.now()),
      lockedAt = None,
      lastLoginAt = None,
      unregisteredAt = None,
      createdAt = Instant.now(),
      updatedAt = None
    )

    val (sellerAName, sellerAActor) = ("sellerA", UserActor.createUserActor(sellerA, userUnicityActorRef))

    val (bidderAName, bidderAUUID) = ("francois", UUID.randomUUID())

    val instantNow = Instant.now()

    val auction = Auction(
      UUID.randomUUID(),
      None, None, None,
      sellerA.userId,
      UUID.randomUUID(), UUID.randomUUID(), AuctionType.AUCTION,
      "Eiffel tower", "", 2010,
      UUID.randomUUID(), Nil, Nil, None,
      Nil,
      0.10, 0.10, 0.10, None,
      1, 1,
      instantNow.plusSeconds(5), None, instantNow.plusSeconds(60 * 60 * 24),
      // instantNow, None, instantNow.plusSeconds(60 * 60 * 24),
      hasAutomaticRenewal = true, hasTimeExtension = true,
      0, 0, 0,
      "EUR",
      None, Nil,
      None, None,
      false,
      instantNow
    )

    sellerAActor ! RegisterUser(sellerA, Instant.now())
    Thread.sleep(3000)

    val auctionActorRef: ActorRef = AuctionActor.createAuctionActor(auction)

    auctionActorRef ! ScheduleAuction(auction)
    // auctionActorRef ! StartAuction(auction)
    // auctionActorRef ! CloseAuction(auction.auctionId.get, UUID.randomUUID(), UUID.randomUUID(), "Closed manually", Instant.now())
    auctionActorRef ! PlaceBid(UsersBid(UUID.randomUUID(), bidderAName, bidderAUUID, 1, 1.00, Instant.now()))

    Thread.sleep(3000)
    sellerAActor ! LockUser(sellerA.userId, UserReason.UNPAID_INVOICE, UUID.randomUUID(), Instant.now())
    Thread.sleep(3000)
    sellerAActor ! UnlockUser(sellerA.userId, Instant.now())

    Thread.sleep(60000)

    actorSystem.terminate()

    Ok
  }

  def crawler = Action { implicit request =>

    // http://doc.akka.io/docs/akka-http/current/scala/http/client-side/request-level.html

    implicit val system = ActorSystem("test")

    val decider: Supervision.Decider = {
      case _: ArithmeticException => Supervision.Resume
      case e =>
        Logger.error("==========> Supervision.Decider caught exception", e)
        Supervision.Stop
    }

    implicit val materializer: ActorMaterializer = ActorMaterializer(
      ActorMaterializerSettings(system).withSupervisionStrategy(decider))

    val delimiter: Flow[ByteString, ByteString, NotUsed] =
      Framing.delimiter(
        ByteString("\r\n"),
        maximumFrameLength = 100000,
        allowTruncation = true)

    val numberOfUrlsProcessedInParallel = 1

    /**
      * Produces a list of URLs and their HTML content, this URLs are the base URLs where we can find
      * the first and the last page number where auctions are listed.
      */
    val getHtmlContentFromBaseUrl: Flow[PriceCrawlerUrl, BasePriceCrawlerUrlWithHtmlContent, NotUsed] =
      Flow[PriceCrawlerUrl].mapAsync[BasePriceCrawlerUrlWithHtmlContent](numberOfUrlsProcessedInParallel) { priceCrawlerUrl =>
        Logger.info(s"Processing WEBSITE ${priceCrawlerUrl.website} url ${priceCrawlerUrl.url}")

        val htmlContentF: Future[String] = Http().singleRequest(HttpRequest(uri = priceCrawlerUrl.url)).flatMap {
          case res if res.status.isSuccess =>
            res.entity.dataBytes.runFold(ByteString(""))(_ ++ _).map(_.utf8String)

          case res =>
            Logger.error(s"Unable to access website ${priceCrawlerUrl.website} with url ${priceCrawlerUrl.url} error ${res.status}")
            throw new ResourceUnavailable("sUnable to access website ${priceCrawlerUrl.website} with url ${priceCrawlerUrl.url} error ${res.status}")
        }

        htmlContentF.map(htmlContent => priceCrawlerUrl -> htmlContent)
      }

    /**
      * Generates a list of URLs to eventually parse auctions from,
      * http://www.andycot.fr/...&page=1
      * http://www.andycot.fr/...&page=2
      * http://www.andycot.fr/...&page=3
      * ...
      * http://www.andycot.fr/...&page=26
      */
    val generatePagedUrlsFromBaseUrl: Flow[BasePriceCrawlerUrlWithHtmlContent, Seq[PriceCrawlerUrlContent], NotUsed] =
      Flow[BasePriceCrawlerUrlWithHtmlContent].map {
        // TODO other case ...
        case (priceCrawlerUrl, htmlContent) if priceCrawlerUrl.website == PriceCrawlerWebsite.DCP =>
          val priceCrawlerUrlContents = PriceCrawlerDCP.getPagedUrls(priceCrawlerUrl, htmlContent).foldLeft(Seq.empty[PriceCrawlerUrlContent]) {
            case (acc, url) if acc.isEmpty =>
              acc :+ PriceCrawlerUrlContent(priceCrawlerUrl.copy(url = url), Some(htmlContent))

            case (acc, url) =>
              acc :+ PriceCrawlerUrlContent(priceCrawlerUrl.copy(url = url), None)
          }

          priceCrawlerUrlContents
      }

    //
    //
    //
    val priceCrawlerUrlGraphStage: Graph[SourceShape[PriceCrawlerUrl], NotUsed] = new PriceCrawlerUrlGraphStage
    val priceCrawlerUrlSource: Source[PriceCrawlerUrl, NotUsed] = Source.fromGraph(priceCrawlerUrlGraphStage)

    val priceCrawlerAuctionsGraphStage: PriceCrawlerAuctionsGraphStage = new PriceCrawlerAuctionsGraphStage
    val priceCrawlerAuctionsFlow: Flow[PriceCrawlerUrlContent, PriceCrawlerAuction, NotUsed] = Flow.fromGraph(priceCrawlerAuctionsGraphStage)

    //
    // This code allows to do nearly the same thing as the PriceCrawlerAuctionsGraphStage
    //
    //    val t = priceCrawlerUrlSource
    //      .via(getHtmlContentFromBaseUrl)
    //      .via(generatePagedUrlsFromBaseUrl)
    //      .flatMapConcat { urls =>
    //        Source.unfoldAsync(urls) {
    //          case (PriceCrawlerUrlContent(url, Some(htmlContent)) :: tail) =>
    //            Logger.info(s"ExtractAuctions $url")
    //            val auctions: List[PriceCrawlerAuction] = PriceCrawlerDCP.extractAuctions(htmlContent)
    //            val alreadyRecorded = priceCrawlerUrlService.auctionsAlreadyRecorded(auctions)
    //
    //            Logger.info(s"PriceCrawlerAuctionsGraphStage.processHtmlContent auctionIds=${auctions.map(_.auctionId)}")
    //
    //            if (alreadyRecorded.length == auctions.length && auctions.nonEmpty) {
    //              Future.successful(None)
    //            } else {
    //              Future.successful(Some(tail, alreadyRecorded))
    //            }
    //
    //          case (PriceCrawlerUrlContent(url, None) :: tail) =>
    //            Logger.info(s"call getHtmlContent $url")
    //            getHtmlContent(url).map { htmlContent =>
    //              Logger.info(s"ExtractionAuctions $url")
    //              val auctions: List[PriceCrawlerAuction] = PriceCrawlerDCP.extractAuctions(htmlContent)
    //              val alreadyRecorded: Seq[PriceCrawlerAuction] = priceCrawlerUrlService.auctionsAlreadyRecorded(auctions)
    //
    //              Logger.info(s"auctionIds=${auctions.map(_.auctionId)}")
    //
    //              if (alreadyRecorded.length == auctions.length && auctions.nonEmpty) {
    //                None
    //              } else {
    //                Some(tail, alreadyRecorded)
    //              }
    //            }
    //
    //          case _ =>
    //            Logger.info("None")
    //            Future.successful(None)
    //        }
    //      }
    //      .runForeach(p4seq)

    val r = priceCrawlerUrlSource
      .throttle(1, imNotARobot(30, 30), 1, ThrottleMode.Shaping)
      .via(getHtmlContentFromBaseUrl)
      .via(generatePagedUrlsFromBaseUrl)
      .flatMapConcat(urls =>
        Source
          .fromIterator(() => urls.toIterator)
          .throttle(1, imNotARobot(10, 10), 1, ThrottleMode.Shaping)
          .via(priceCrawlerAuctionsFlow)
      )
      .map { auction =>
        priceCrawlerAuctionService.createOne(auction).recover {
          case NonFatal(e) => Logger.error("MongoDB persistence error", e)
        }
        auction
      }
      .runForeach(p4)

    Ok
  }

  /**
    *
    * @param base
    * @param range
    * @return
    */
  def imNotARobot(base: Int, range: Int): FiniteDuration = {
    val r = scala.util.Random
    (base + r.nextInt(range)).seconds
  }

  def p4(priceCrawlerAuction: PriceCrawlerAuction): Unit = {
    // println(s"AuctionController.p4 received auctionId ================> ${priceCrawlerAuction.auctionId}")
    // Thread.sleep(50)
  }
}
