import com.google.inject.AbstractModule
import persistence.{EmailUnicityRepo, EmailUnicitySql}

class Module extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[EmailUnicityRepo]).to(classOf[EmailUnicitySql])
  }
}