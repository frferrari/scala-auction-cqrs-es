package actors.userSupervisor

import actors.ActorCommonsSpec
import actors.user.UserActor.{CurrentStateReply, RegistrationRejectedReply, UserRegisteredReply}
import actors.user.UserActorHelpers
import actors.user.fsm.{InactiveUser, RegisteredState}
import actors.userUnicity.UserUnicityActor
import actors.userUnicity.UserUnicityActor.UserUnicityListReply
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import cqrs.commands._
import models.RegistrationRejectedReason
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import play.api.inject.BindingKey
import play.api.inject.guice.GuiceApplicationBuilder

/**
  * Created by Francois FERRARI on 24/05/2017
  */
class UserSupervisorSpec
  extends TestKit(ActorSystem("AuctionSystem"))
    with ActorCommonsSpec
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with UserActorHelpers {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  val app = new GuiceApplicationBuilder().build()
  val injector = app.injector
  val userUnicityActorRef = injector.instanceOf(BindingKey(classOf[ActorRef]).qualifiedWith(UserUnicityActor.name))
  val userSupervisorActorRef = system.actorOf(Props(new UserSupervisor(userUnicityActorRef)), name = UserSupervisor.name)

  "A User Supervisor" should {

    val seller1 = makeUser("user1@pluto.space", "user1", "Robert1", "John1")
    val seller2 = makeUser("user1@pluto.space", "user2", "Robert2", "John2")
    val seller3 = makeUser("user3@pluto.space", "user1", "Robert3", "John3")
    val seller4 = makeUser("user4@pluto.space", "user4", "Robert4", "John4")

    "be able to create a user with an unused email and unused nickname (first attempt to record a user)" in {
      userSupervisorActorRef ! CreateUser(seller1)
      expectMsg(UserRegisteredReply)
    }

    "successfully check that the UserUnicityList contains seller1's emailAddress/nickName" in {
      userUnicityActorRef ! GetUserUnicityList
      expectMsgPF() {
        case (UserUnicityListReply(userUnicityList)) if userUnicityList.length == 1 &&
          userUnicityList.exists(uu => uu.emailAddress == seller1.emailAddress && uu.nickName == seller1.nickName) => ()
      }
    }

    "successfully check that the seller1's actor is in RegisteredState" in {
      getUserActorSelection(seller1.userId) ! GetUserCurrentState
      expectMsgPF() {
        case (CurrentStateReply(RegisteredState, InactiveUser)) => ()
      }
    }

    "be refused to record a user with an already used email" in {
      userSupervisorActorRef ! CreateUser(seller2)
      expectMsg(RegistrationRejectedReply(RegistrationRejectedReason.EMAIL_ALREADY_EXISTS))
    }

    "successfully check that the UserUnicityList contains only seller1's emailAddress/nickName" in {
      userUnicityActorRef ! GetUserUnicityList
      expectMsgPF() {
        case (UserUnicityListReply(userUnicityList)) if userUnicityList.length == 1 &&
          userUnicityList.exists(uu => uu.emailAddress == seller1.emailAddress && uu.nickName == seller1.nickName) => ()
      }
    }

    "be refused to record a user with an already used nickname" in {
      userSupervisorActorRef ! CreateUser(seller3)
      expectMsg(RegistrationRejectedReply(RegistrationRejectedReason.NICKNAME_ALREADY_EXISTS))
    }

    "successfully check that the UserUnicityList still contains only seller1's emailAddress/nickName" in {
      userUnicityActorRef ! GetUserUnicityList
      expectMsgPF() {
        case (UserUnicityListReply(userUnicityList)) if userUnicityList.length == 1 &&
          userUnicityList.exists(uu => uu.emailAddress == seller1.emailAddress && uu.nickName == seller1.nickName) => ()
      }
    }

    "be able to record a user with an unused email and unused nickname" in {
      userSupervisorActorRef ! CreateUser(seller4)
      expectMsg(UserRegisteredReply)
    }

    "successfully check that the UserUnicityList contains 2 seller1's and seller4's emailAddress/nickName" in {
      userUnicityActorRef ! GetUserUnicityList
      expectMsgPF() {
        case (UserUnicityListReply(userUnicityList)) if userUnicityList.length == 2 &&
        userUnicityList.exists(uu => uu.emailAddress == seller1.emailAddress && uu.nickName == seller1.nickName) &&
        userUnicityList.exists(uu => uu.emailAddress == seller4.emailAddress && uu.nickName == seller4.nickName) => ()
      }
    }
  }
}
