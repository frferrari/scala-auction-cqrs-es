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
  * Created by Francois FERRARI on 21/05/2017
  */
class AuctionActorSpec2() extends TestKit(ActorSystem("AuctionSystem"))
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

  val auction = makeAuction(
    startPrice = 0.10,
    bidIncrement = 0.10,
    startsAt = Instant.now().plusSeconds(4),
    lastsSeconds = 10,
    hasAutomaticRenewal = true,
    hasTimeExtension = false,
    sellerId = seller.userId
  )

  "An AUCTION W/O reserve price W/automatic renewal W/O bidders" should {

    "successfully create a User (Seller)" in {
      userSupervisorActorRef ! CreateUser(seller)
      expectMsg(UserRegisteredReply)
    }

    "successfully create an auction in Scheduled state" in {
      auctionSupervisorActorRef ! CreateAuction(auction)
      expectMsg(AuctionScheduledReply)
    }

    "successfully check that the auction is now in StartedState" in {
      expectNoMsg(secondsToWaitForAuctionStart(auction).seconds)
      getAuctionActorSelection(auction.auctionId) ! GetCurrentState
      expectMsgPF() {
        case (CurrentStateReply(StartedState, _: ActiveAuction)) => ()
      }
    }

    "be RESTARTED when it has reached it's end time" in {
      expectNoMsg(secondsToWaitForAuctionEnd(auction).seconds)

      getAuctionActorSelection(auction.auctionId) ! GetCurrentState
      expectMsgPF() {
        case CurrentStateReply(StartedState, ActiveAuction(restartedAuction))
          if restartedAuction.bids.isEmpty &&
            restartedAuction.currentPrice == restartedAuction.startPrice &&
            restartedAuction.closedBy.isEmpty &&
            restartedAuction.closedAt.isEmpty &&
            restartedAuction.startsAt == auction.endsAt &&
            restartedAuction.endsAt.isAfter(restartedAuction.startsAt) &&
            ! restartedAuction.isSold
        => ()
      }
    }
  }
}
