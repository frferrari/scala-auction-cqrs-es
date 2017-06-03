package actors.userUnicity

import java.time.Instant

import actors.ActorCommonsSpec
import actors.user.UserActor
import actors.user.UserActor.{RegistrationRejectedReply, UserRegisteredReply}
import actors.userUnicity.UserUnicityActor.UserUnicityListReply
import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit}
import cqrs.commands._
import models.RegistrationRejectedReason
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import play.api.inject.BindingKey
import play.api.inject.guice.GuiceApplicationBuilder

/**
  * Created by Francois FERRARI on 24/05/2017
  */
class UserUnicityViaUserSpec
  extends TestKit(ActorSystem("UserActorSpec"))
    with ActorCommonsSpec
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  val app = new GuiceApplicationBuilder().build()
  val injector = app.injector
  val userUnicityActorRef = injector.instanceOf(BindingKey(classOf[ActorRef]).qualifiedWith(UserUnicityActor.name))

  "A USER actor" should {

    val seller1 = makeUser("user1@pluto.space", "user1", "Robert1", "John1")
    val seller2 = makeUser("user1@pluto.space", "user2", "Robert2", "John2")
    val seller3 = makeUser("user3@pluto.space", "user1", "Robert3", "John3")
    val seller4 = makeUser("user4@pluto.space", "user4", "Robert4", "John4")

    val (sellerName1, sellerActor1) = (seller1.nickName, UserActor.createUserActor(seller1, userUnicityActorRef))
    val (sellerName2, sellerActor2) = (seller2.nickName, UserActor.createUserActor(seller2, userUnicityActorRef))
    val (sellerName3, sellerActor3) = (seller3.nickName, UserActor.createUserActor(seller3, userUnicityActorRef))
    val (sellerName4, sellerActor4) = (seller4.nickName, UserActor.createUserActor(seller4, userUnicityActorRef))

    "be able to record a user with an unused email and unused nickname (first attempt to record a user)" in {
      sellerActor1 ! RegisterUser(seller1, Instant.now())
      expectMsg(UserRegisteredReply)
    }

    "successfully check that the UserUnicityList contains seller1's emailAddress/nickName" in {
      userUnicityActorRef ! GetUserUnicityList
      expectMsgPF() {
        case (UserUnicityListReply(userUnicityList)) if userUnicityList.length == 1 &&
          userUnicityList.exists(uu => uu.emailAddress == seller1.emailAddress && uu.nickName == seller1.nickName) => ()
      }
    }

    "be refused to record a user with an already used email" in {
      sellerActor2 ! RegisterUser(seller2, Instant.now())
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
      sellerActor3 ! RegisterUser(seller3, Instant.now())
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
      sellerActor4 ! RegisterUser(seller4, Instant.now())
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
