package actors.userUnicity

import java.time.Instant

import actors.ActorCommonsSpec
import actors.userUnicity.UserUnicityActor.{UserUnicityEmailAlreadyRecordedReply, UserUnicityNickNameAlreadyRecordedReply, UserUnicityRecordedReply}
import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit}
import cqrs.commands._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import play.api.inject.BindingKey
import play.api.inject.guice.GuiceApplicationBuilder

/**
  * Created by Francois FERRARI on 24/05/2017
  */
class UserUnicitySpec
  extends TestKit(ActorSystem("AuctionSystem"))
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
  val userUnicityActorRef: ActorRef = injector.instanceOf(BindingKey(classOf[ActorRef]).qualifiedWith(UserUnicityActor.name))

//  val injector = new GuiceInjectorBuilder()
//    .overrides(bind(classOf[EmailUnicityRepo]).to[EmailUnicityMock])
//    .injector
//
//  implicit val emailUnicityMock: EmailUnicityMock = injector.instanceOf[EmailUnicityMock]

  "A UserUnicity actor" should {

//    val userUnicityActor = system.actorOf(UserUnicityActor.props, "userUnicityActor")

    val seller1 = makeUser("user1@pluto.space", "user1", "Robert1", "John1")
    val seller2 = makeUser("user1@pluto.space", "user2", "Robert2", "John2")
    val seller3 = makeUser("user3@pluto.space", "user1", "Robert3", "John3")
    val seller4 = makeUser("user4@pluto.space", "user4", "Robert4", "John4")

    "accept to record a user with an unused email and unused nickname (first attempt to record a user)" in {
      userUnicityActorRef ! RecordUserUnicity(seller1, self, Instant.now())
      expectMsgPF() {
        case (reply: UserUnicityRecordedReply) => ()
      }
    }

    "refuse to record a user with an already used email" in {
      userUnicityActorRef ! RecordUserUnicity(seller2, self, Instant.now())
      expectMsgPF() {
        case (reply: UserUnicityEmailAlreadyRecordedReply) => ()
      }
    }

    "refuse to record a user with an already used nickname" in {
      userUnicityActorRef ! RecordUserUnicity(seller3, self, Instant.now())
      expectMsgPF() {
        case (reply: UserUnicityNickNameAlreadyRecordedReply) => ()
      }
    }

    "accept to record a user with an unused email and unused nickname" in {
      userUnicityActorRef ! RecordUserUnicity(seller4, self, Instant.now())
      expectMsgPF() {
        case (reply: UserUnicityRecordedReply) => ()
      }
    }
  }
}
