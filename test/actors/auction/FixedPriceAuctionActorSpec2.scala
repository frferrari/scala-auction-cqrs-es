package actors.auction

import java.time.Instant

import actors.ActorCommonsSpec
import actors.auction.AuctionActor._
import actors.auction.fsm.{ActiveAuction, StartedState}
import actors.auctionSupervisor.AuctionSupervisor
import actors.user.UserActor.UserRegisteredReply
import actors.userSupervisor.UserSupervisor
import actors.userUnicity.UserUnicityActor
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import cqrs.commands.{CreateAuction, CreateUser, GetCurrentState}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import play.api.inject.BindingKey
import play.api.inject.guice.GuiceApplicationBuilder

import scala.concurrent.duration._

/**
  * Created by Francois FERRARI on 17/05/2017
  */
class FixedPriceAuctionActorSpec2() extends TestKit(ActorSystem("AuctionSystem"))
  with ActorCommonsSpec
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with AuctionActorHelpers {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  val app = new GuiceApplicationBuilder().build()
  val injector = app.injector
  val userUnicityActorRef = injector.instanceOf(BindingKey(classOf[ActorRef]).qualifiedWith(UserUnicityActor.name))
  val userSupervisorActorRef = system.actorOf(Props(new UserSupervisor(userUnicityActorRef)), name = UserSupervisor.name)
  val auctionSupervisorActorRef = system.actorOf(Props(new AuctionSupervisor()), name = AuctionSupervisor.name)

  val seller = makeUser("contact@pluto.space", "hhgg", "Robert", "John")
  val bidderA = makeUser("bidderA@pluto.space", "bidderA", "BidderA", "John")

  val auction = makeFixedPriceAuction(
    startPrice = 0.10,
    startsAt = Instant.now().plusSeconds(2),
    lastsSeconds = 5,
    stock= 10,
    hasAutomaticRenewal = true,
    sellerId = seller.userId
  )

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
      expectMsg(AuctionScheduledReply)
    }

    "be RESTARTED and NOT SOLD" in {
      expectNoMsg(secondsToWaitForAuctionEnd(auction).seconds)

      getAuctionActorSelection(auction.auctionId) ! GetCurrentState
      expectMsgPF() {
        case CurrentStateReply(StartedState, ActiveAuction(activeAuction))
          if activeAuction.bids.isEmpty &&
            activeAuction.startsAt == auction.endsAt &&
            activeAuction.endsAt.isAfter(auction.endsAt) &&
            !activeAuction.isSold
        => ()
      }
    }
  }
}
