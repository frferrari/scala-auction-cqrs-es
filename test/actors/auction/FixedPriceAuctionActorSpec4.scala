package actors.auction

import java.time.Instant

import actors.ActorCommonsSpec
import actors.auction.AuctionActor._
import actors.auction.fsm.{ActiveAuction, ClosedState, FinishedAuction, StartedState}
import actors.auctionSupervisor.AuctionSupervisor
import actors.user.UserActor.UserRegisteredReply
import actors.userSupervisor.UserSupervisor
import actors.userUnicity.UserUnicityActor
import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import cqrs.UsersBid
import cqrs.commands._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import play.api.inject.BindingKey
import play.api.inject.guice.GuiceApplicationBuilder

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Created by Francois FERRARI on 17/05/2017
  */
class FixedPriceAuctionActorSpec4() extends TestKit(ActorSystem("AuctionSystem"))
  with ActorCommonsSpec
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with AuctionActorHelpers {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
    Await.ready(system.terminate(), 5.seconds)
  }

  val app = new GuiceApplicationBuilder().build()
  val injector = app.injector
  val userUnicityActorRef = injector.instanceOf(BindingKey(classOf[ActorRef]).qualifiedWith(UserUnicityActor.name))
  val userSupervisorActorRef = system.actorOf(Props(new UserSupervisor(userUnicityActorRef)), name = UserSupervisor.name)
  val auctionSupervisorActorRef = system.actorOf(Props(new AuctionSupervisor()), name = AuctionSupervisor.name)

  val seller = makeUser("contact@pluto.space", "hhgg", "Robert", "John")
  val bidderA = makeUser("bidderA@pluto.space", "bidderA", "BidderA", "John")

  val originalStock = 10
  val requestedStock = 8

  val auction = makeFixedPriceAuction(
    startPrice = 0.10,
    startsAt = Instant.now(),
    lastsSeconds = 30,
    stock = originalStock,
    hasAutomaticRenewal = false,
    sellerId = seller.userId
  )

  implicit val askTimeout = Timeout(2.seconds)

  "A scheduled FIXED PRICE auction" should {

    "successfully create a User (Seller)" in {
      userSupervisorActorRef ! CreateUser(seller)
      expectMsg(UserRegisteredReply)
    }

    "successfully create a User (bidderA)" in {
      userSupervisorActorRef ! CreateUser(bidderA)
      expectMsg(UserRegisteredReply)
    }

    "successfully create an auction in StartedState" in {
      auctionSupervisorActorRef ! CreateAuction(auction)
      expectMsg(AuctionStartedReply)
    }

    "accept a bid on an auction for a part of the stock" in {
      getAuctionActorSelection(auction.auctionId) ! PlaceBid(UsersBid(auction.auctionId, bidderA.nickName, bidderA.userId, requestedStock, auction.currentPrice, Instant.now()))
      expectMsg(BidPlacedReply)
    }

    "successfully check that the auction is closed and that it contains a reference to the cloned auction" in {
      expectNoMsg(2.seconds)

      getAuctionActorSelection(auction.auctionId) ! GetCurrentState
      expectMsgPF() {
        case CurrentStateReply(ClosedState, FinishedAuction(finishedAuction))
          if finishedAuction.bids.length == 1 &&
            finishedAuction.stock == 0 &&
            finishedAuction.originalStock == requestedStock &&
            finishedAuction.isSold &&
            finishedAuction.clonedToAuctionId.isDefined => ()
      }
    }

    "successfully check that the cloned auction is in StartedState and that it contains a reference to its parent auction with the remaining stock" in {
      expectNoMsg(2.seconds)

      Await.result((getAuctionActorSelection(auction.auctionId) ? GetCurrentState).mapTo[CurrentStateReply], 2.seconds) match {
        case CurrentStateReply(ClosedState, FinishedAuction(finishedAuction)) =>
          // Get the CurrentState of the cloned auction
          getAuctionActorSelection(finishedAuction.clonedToAuctionId.get) ! GetCurrentState

          expectMsgPF() {
            case CurrentStateReply(StartedState, ActiveAuction(clonedAuction))
              if clonedAuction.clonedFromAuctionId.contains(finishedAuction.auctionId) &&
                 clonedAuction.clonedToAuctionId.isEmpty &&
                 clonedAuction.endsAt == finishedAuction.endsAt &&
                 clonedAuction.stock == (originalStock-requestedStock) &&
                clonedAuction.originalStock == (originalStock-requestedStock) => ()
          }

          // To make the cloned auction unsubscribe from the seller actor's events
          getAuctionActorSelection(finishedAuction.clonedToAuctionId.get) ! PoisonPill

        case _ =>
          fail
      }
    }

    "wait for auction actor end" in {
      // To make the original auction unsubscribe from the seller actor's events
      getAuctionActorSelection(auction.auctionId) ! PoisonPill
      expectNoMsg(2.seconds)
    }
  }
}
