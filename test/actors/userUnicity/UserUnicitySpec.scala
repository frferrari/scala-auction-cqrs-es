package actors.userUnicity

import java.time.Instant

import actors.ActorCommonsSpec
import actors.userUnicity.UserUnicityActor.{UserUnicityEmailAlreadyRegisteredReply, UserUnicityNickNameAlreadyRegisteredReply, UserUnicityRecordedReply}
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import cqrs.commands._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

/**
  * Created by Francois FERRARI on 24/05/2017
  */
class UserUnicitySpec
  extends TestKit(ActorSystem("UserActorSpec"))
    with ActorCommonsSpec
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

//  val injector = new GuiceInjectorBuilder()
//    .overrides(bind(classOf[EmailUnicityRepo]).to[EmailUnicityMock])
//    .injector
//
//  implicit val emailUnicityMock: EmailUnicityMock = injector.instanceOf[EmailUnicityMock]

  "A UserUnicity actor" should {

    val userUnicityActor = system.actorOf(UserUnicityActor.props, "userUnicityActor")

    val seller1 = makeUser("user1@pluto.space", "user1", "Robert1", "John1")
    val seller2 = makeUser("user1@pluto.space", "user2", "Robert2", "John2")
    val seller3 = makeUser("user3@pluto.space", "user1", "Robert3", "John3")
    val seller4 = makeUser("user4@pluto.space", "user4", "Robert4", "John4")

    "accept to record a user with an unused email and unused nickname (first attempt to record a user)" in {
      userUnicityActor ! RecordUserUnicity(seller1, Instant.now())
      expectMsg(UserUnicityRecordedReply)
    }

    "refuse to record a user with an already used email" in {
      userUnicityActor ! RecordUserUnicity(seller2, Instant.now())
      expectMsg(UserUnicityEmailAlreadyRegisteredReply)
    }

    "refuse to register a user with an already used nickname" in {
      userUnicityActor ! RecordUserUnicity(seller3, Instant.now())
      expectMsg(UserUnicityNickNameAlreadyRegisteredReply)
    }

    "accept to record a user with an unused email and unused nickname" in {
      userUnicityActor ! RecordUserUnicity(seller4, Instant.now())
      expectMsg(UserUnicityRecordedReply)
    }
  }
}
