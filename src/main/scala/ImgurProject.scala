import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

import akka.actor._
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.json4s.JsonAST._
import org.json4s.jackson.JsonMethods.parse

import scala.collection._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scalaj.http.{Http, HttpOptions}



case object FetchData
case object PostData


case class Post(link: String, images: List[JValue])


object Fetcher {
  var etag = ""

  def getPosts: Future[Unit] = {
    var request = Http("https://api.imgur.com/3/gallery/hot/").header("Authorization", "Client-ID b5321b8f5cb0519").option(HttpOptions.connTimeout(5000))

    if (!etag.isEmpty)
      request = request.header("If-None-Match", etag)

    val response = request.asString
    val etagToken = response.headers get "ETag" get 0

    if (!etagToken.isEmpty)
      etag = etagToken

    println(s"Common etag: $etag")
    println(s"Current etagToken: $etag")

    println(response.code)

    if (response.code == 200) {
      val ast = parse(response.body)

      val data: List[Post] = for {
        JObject(item) <- ast
        JField("link", JString(link)) <- item
        JArray(images) <- ast \\ "images"
      } yield Post(link, images)

      Future {
        for {
          post <- data
          JObject(image) <- post.images
          JField("link", JString(link)) <- image
        } ParsedData.queue.add(link)
        Unit
      }
    } else {
      Future {
        Unit
      }
    }
  }
}

object ParsedData {
  val queue = new ConcurrentLinkedQueue[String]()
}

object BotWrapper {
  val postToChannelUrl = "https://api.telegram.org/bot582060096:AAFQy54HegV3MARTDyvU-gVko1z2QE_BQ48/sendMessage"
  val channelId = "@nobodyneedthatshit"
  val request = Http(postToChannelUrl).option(HttpOptions.followRedirects(true)).option(HttpOptions.connTimeout(5000)).proxy("38.96.9.236", 8008)

}

class FetchActor extends Actor with ActorLogging {
  val fetchPeriod: FiniteDuration = 2.hours
  var postPeriod: FiniteDuration = 15.seconds
  val counter = new AtomicInteger()

  def scheduleTimer(): Cancellable = {
    context.system.scheduler.scheduleOnce(
      fetchPeriod, context.self, FetchData
    )
  }

  def sendToChannel(link: String): Unit = {

    val data = Seq("chat_id" -> BotWrapper.channelId, "text" -> link)
    val response = BotWrapper.request.postForm(data).asString
    println(response.body)
  }

  override def preStart(): Unit = {
    scheduleTimer()
  }

  override def postRestart(reason: Throwable): Unit = {}

  override def receive: PartialFunction[Any, Unit] = {
    case FetchData => {
      log.warning("FetchData message")
      val Freq = Fetcher.getPosts

      Freq onComplete(_ =>
        {
          context.system.scheduler.scheduleOnce(postPeriod, context.self, PostData)
          context.system.scheduler.scheduleOnce(fetchPeriod, context.self, FetchData)
        }
        )
    }

    case PostData => {
      log.warning("PostData message")

      val link = ParsedData.queue.poll()
      sendToChannel(link)
      context.system.scheduler.scheduleOnce(postPeriod, context.self, PostData)
    }
    case r =>
      log.warning(s"Unexpected message: $r")

  }
}

object XMain extends App {
  val config = ConfigFactory.parseString(
    """
       akka.loglevel = "DEBUG"
       akka.actor.debug {
        receive = on
        lifecycle = on
       }
    """
  )
  val system = ActorSystem("system", config)
  val mainActor = system.actorOf(Props[FetchActor], "fetchActor")
  Await.result(system.whenTerminated, Duration.Inf)
}

