package controllers

import java.time.{Instant, LocalDate}
import java.util.UUID
import javax.inject.{Inject, Named, Singleton}

import actors.auction.AuctionActor
import actors.user.UserActor
import actors.userUnicity.UserUnicityActor
import akka.actor.{ActorRef, ActorSystem}
import cqrs.UsersBid
import cqrs.commands._
import models._
import play.api.mvc.{Action, Controller}

/**
  * Created by Francois FERRARI on 31/05/2017
  */
@Singleton
class AuctionController @Inject()(@Named(UserUnicityActor.name) userUnicityActorRef: ActorRef) extends Controller {

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
}
