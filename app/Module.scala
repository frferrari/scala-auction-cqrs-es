import actors.userUnicity.UserUnicityActor
import actors.{ActorSystemScheduler, AuctionSystemScheduler}
import com.google.inject.AbstractModule
import play.api.Logger
import play.api.libs.concurrent.AkkaGuiceSupport

class Module extends AbstractModule with AkkaGuiceSupport {
  override def configure(): Unit = {
    Logger.info("Inside Module ...")
    bindActor[UserUnicityActor](UserUnicityActor.name)
    bind(classOf[ActorSystemScheduler]).to(classOf[AuctionSystemScheduler]).asEagerSingleton()
  }
}