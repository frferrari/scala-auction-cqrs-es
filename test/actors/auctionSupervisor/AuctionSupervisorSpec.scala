package actors.auctionSupervisor

import java.time.Instant

import actors.ActorCommonsSpec
import actors.auction.AuctionActor.{AuctionStartedReply, CurrentStateReply}
import actors.auction.AuctionActorHelpers
import actors.auction.fsm.{InactiveAuction, StartedState}
import actors.userSupervisor.UserSupervisor
import actors.userUnicity.UserUnicityActor
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import cqrs.commands._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import play.api.inject.BindingKey
import play.api.inject.guice.GuiceApplicationBuilder

/**
  * Created by Francois FERRARI on 24/05/2017
  */
class AuctionSupervisorSpec
  extends TestKit(ActorSystem("AuctionSystem"))
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

  "An Auction Supervisor" should {

    val seller = makeUser("user1@pluto.space", "user1", "Robert1", "John1")
    val auction = makeAuction(
      startPrice = 0.10,
      bidIncrement = 0.10,
      startsAt = Instant.now(),
      lastsSeconds = 30,
      hasAutomaticRenewal = false,
      hasTimeExtension = false,
      seller.userId
    )

    "be able to create an auction" in {
      auctionSupervisorActorRef ! CreateAuction(auction)
      expectMsg(AuctionStartedReply)
    }

    "successfully check that the auction actor is in StartedState" in {
      getAuctionActorSelection(auction.auctionId) ! GetAuctionCurrentState
      expectMsgPF() {
        case (CurrentStateReply(StartedState, InactiveAuction)) => ()
      }
    }
  }
}
