package actors.userUnicity

import java.time.Instant

import actors.ActorCommonsSpec
import actors.user.UserActor
import actors.user.UserActor.{RegistrationRejectedReply, UserRegisteredReply}
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import cqrs.commands._
import models.RegistrationRejectedReason
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import persistence.EmailUnicityRepo
import play.api.inject.guice.GuiceInjectorBuilder
import play.api.inject.bind

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

    val injector = new GuiceInjectorBuilder()
      .overrides(bind(classOf[EmailUnicityRepo]).to[EmailUnicityMock])
      .injector

    implicit val emailUnicityMock: EmailUnicityMock = injector.instanceOf[EmailUnicityMock]

    "A USER actor" should {

      val seller1 = makeUser("user1@pluto.space", "user1", "Robert", "John")
      val (sellerName1, sellerActor1) = (seller1.nickName, UserActor.createUserActor(seller1))

      val seller2 = makeUser("user1@pluto.space", "user2", "Robert", "John")
      val (sellerName2, sellerActor2) = (seller2.nickName, UserActor.createUserActor(seller2))

      val seller3 = makeUser("user3@pluto.space", "user1", "Robert", "John")
      val (sellerName3, sellerActor3) = (seller3.nickName, UserActor.createUserActor(seller3))

      "accept to register a user with an unused email and unused nickname" in {
        sellerActor1 ! RegisterUser(seller1, Instant.now())
        expectMsg(UserRegisteredReply)
      }

      "refuse to register a user with an already used email" in {
        sellerActor2 ! RegisterUser(seller2, Instant.now())
        expectMsgPF() {
          case RegistrationRejectedReply(_, RegistrationRejectedReason.EMAIL_OR_NICKNAME_ALREADY_EXISTS) => ()
        }
      }

      "refuse to register a user with an already used nickname" in {
        sellerActor3 ! RegisterUser(seller3, Instant.now())
        expectMsgPF() {
          case RegistrationRejectedReply(_, RegistrationRejectedReason.EMAIL_OR_NICKNAME_ALREADY_EXISTS) => ()
        }
      }
    }
  }
