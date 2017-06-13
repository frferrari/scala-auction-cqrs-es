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
import akka.stream.scaladsl.{Flow, Framing, Source}
import akka.stream.{ActorMaterializer, Graph, SourceShape}
import akka.util.ByteString
import cqrs.UsersBid
import cqrs.commands._
import models._
import play.api.Logger
import play.api.mvc.{Action, Controller}
import priceCrawler._

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by Francois FERRARI on 31/05/2017
  */
@Singleton
class AuctionController @Inject()(@Named(UserUnicityActor.name) userUnicityActorRef: ActorRef)(implicit priceCrawlerUrlService: PriceCrawlerUrlService, ec: ExecutionContext) extends Controller {

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
    implicit val mat = ActorMaterializer()

    Logger.info("====> inside Crawler")

    val delimiter: Flow[ByteString, ByteString, NotUsed] =
      Framing.delimiter(
        ByteString("\r\n"),
        maximumFrameLength = 100000,
        allowTruncation = true)

    //    val f = Http().singleRequest(Get("http://www.andycot.fr")).flatMap { res =>
    //      val lines = res.entity.dataBytes.via(delimiter).map(_.utf8String)
    //      lines.runForeach { line =>
    //        println(line)
    //      }
    //    }

    //    val r = Source.fromFuture(generateUrls).initialDelay(2.seconds).grouped(2)

    //    val t = Http().singleRequest(HttpRequest(uri = "http://www.andycot.fr")).map { res =>
    //      res.entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
    //        println(body.utf8String)
    //      }
    //    }

    //    r.runForeach(x => println(s"=====> $x\n"))

    //    t.foreach { _ =>
    //      system.terminate()
    //    }

    val numberOfUrlsProcessedInParallel = 1

    /**
      *
      */
    val getHtmlContentFromBaseUrl: Flow[PriceCrawlerUrl, BasePriceCrawlerUrlWithHtmlContent, NotUsed] =
      Flow[PriceCrawlerUrl].mapAsync[BasePriceCrawlerUrlWithHtmlContent](numberOfUrlsProcessedInParallel) { priceCrawlerUrl =>
        Logger.info(s"Processing website ${priceCrawlerUrl.website} url ${priceCrawlerUrl.url}")

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
      *
      */
    val generatePagedUrlsFromBaseUrl: Flow[BasePriceCrawlerUrlWithHtmlContent, (BasePriceCrawlerUrlWithHtmlContent, Seq[String]), NotUsed] =
      Flow[BasePriceCrawlerUrlWithHtmlContent].map {
        case (priceCrawlerUrl, htmlContent) if priceCrawlerUrl.website == PriceCrawlerWebsite.DCP =>
          PriceCrawlerDCP.getPagedUrls(priceCrawlerUrl, htmlContent)
      }

    val generatePriceCrawlerAuctions: Flow[(BasePriceCrawlerUrlWithHtmlContent, Seq[String]), Seq[PriceCrawlerAuction], NotUsed] =
      Flow[(BasePriceCrawlerUrlWithHtmlContent, Seq[String])].map {
        case ((priceCrawlerUrl, htmlContent), auctionUrls) =>
          PriceCrawlerDCP.extractAuctions(htmlContent)
      }

    val priceCrawlerUrlGraphStage: Graph[SourceShape[PriceCrawlerUrl], NotUsed] = new PriceCrawlerUrlGraphStage
    val priceCrawlerUrlSource: Source[PriceCrawlerUrl, NotUsed] = Source.fromGraph(priceCrawlerUrlGraphStage)
    // priceCrawlerUrlSource.take(20).runForeach(p)

    priceCrawlerUrlSource
      .via(getHtmlContentFromBaseUrl)
      .via(generatePagedUrlsFromBaseUrl)
        .via(generatePriceCrawlerAuctions)
      .runForeach(p3)

    Ok
  }

  /**
    *
    * @param s
    */
  def p3(s: Seq[PriceCrawlerAuction]) = {
    println(s"========================> $s")
    Thread.sleep(4000)
  }

  def p2(s: (BasePriceCrawlerUrlWithHtmlContent, Seq[String])) = {
    println(s"${s._1._1.url} --> ${s._2}")
    Thread.sleep(4000)
  }

  def p(url: PriceCrawlerUrl) = {
    // Thread.sleep(5000)
    println(url)
  }
}
