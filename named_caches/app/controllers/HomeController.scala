package controllers
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import play.api.cache._
import play.api.cache.redis.CacheAsyncApi
import play.api.cache.Cached
import play.api.mvc._
import scala.concurrent.duration._
@Singleton
class HomeController @Inject()
(
  cached: Cached,
  // default unqualified instance, equal to "local"
  cache: CacheAsyncApi,
  // instance with DB 1
  @NamedCache("local") local: CacheAsyncApi,
  // instance with DB 2
  @NamedCache("remote") remote: CacheAsyncApi,
  // instance with a custom recovery policy
  @NamedCache("failing") failing: CacheAsyncApi,
  cc: ControllerComponents
)(implicit executionContext: ExecutionContext) extends AbstractController(cc) {
  import Imports._
  private def message(instance: CacheAsyncApi, name: String) = instance.getOrElse("named-caches#message", expiration = 10.seconds) {
    s"This message was set to $name instance at ${now.asString}."
  }

  private def action(instance: CacheAsyncApi, name: String) = instance.get[String]("named-caches#message").map {
    case Some(value) => s"The value is $value"
    case None if name.equalsIgnoreCase("local") =>
      instance.set("named-caches#message", now.asString, 5.seconds)
      s"no cache available for the message $name"
    case None =>
      s"no cache available for the message $name"
  }

  private def remove(instance: CacheAsyncApi, name: String) = instance.remove("named-caches#message")

  private def messageInDefault = action(cache, "default")

  private def messageInLocal = action(local, "local")

  private def messageInRemote = action(remote, "remote")

  def messageInFailing = action(failing, "failing")

  def index = /*cached(_ => "homepage", duration = 4)*/ {
    Action.async {
      for {
        default <- this.messageInDefault
        local <- this.messageInLocal
        remote <- this.messageInRemote
        failing <- this.messageInFailing
      } yield {
        Ok(views.html.index(default, local, remote, failing, now.asString))
      }
    }
  }
}
object Imports {
  implicit class RichDate(val date: LocalDateTime) extends AnyVal {
    def asString = DateTimeFormatter.ofPattern("HH:mm:ss").format(date)
  }
  def now = LocalDateTime.now()
}
