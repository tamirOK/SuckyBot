import java.net.SocketTimeoutException
import java.util.concurrent.{ConcurrentLinkedQueue, TimeoutException}
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
  var lastImage: BigInt = 0

  def getPosts: Future[Unit] = {
    var request = Http("https://api.imgur.com/3/gallery/hot/time/").header("Authorization", "Client-ID b5321b8f5cb0519").option(HttpOptions.connTimeout(5000))
    val response = request.asString
    if (response.code == 200) {
      val bodyData = parse(response.body)
      val images: List[JValue] = for {
        JObject(item) <- bodyData
        JField("images", JArray(images)) <- item
        img <- images
      } yield img
      Future {
        var currentLastImage: BigInt = lastImage
        for {
          img <- images
          JObject(imgObject) <- img
          JField("link", JString(link)) <- imgObject
          JField("datetime", JInt(date)) <- imgObject
          if date > lastImage
        } {
          ParsedData.queue.add(link)
          currentLastImage = currentLastImage.max(date)
        }
        lastImage = currentLastImage
      }
    } else {
      Future()
    }
  }
}

object ParsedData {
  val queue = new ConcurrentLinkedQueue[String]()
}

object BotWrapper {
  val postToChannelUrl = "https://api.telegram.org/bot582060096:AAFQy54HegV3MARTDyvU-gVko1z2QE_BQ48/sendMessage"
  val channelId = "@nobodyneedthatshit"
  val request = Http(postToChannelUrl).option(HttpOptions.followRedirects(true)).option(HttpOptions.connTimeout(5000)).option(HttpOptions.readTimeout(10000)).proxy("38.96.9.236", 8008)

}

class FetchActor extends Actor with ActorLogging {
  val fetchPeriod: FiniteDuration = 60.seconds
  var postPeriod: FiniteDuration = 60.seconds
  val counter = new AtomicInteger()

  def scheduleTimer(): Cancellable = {
    context.system.scheduler.scheduleOnce(
      fetchPeriod, context.self, FetchData
    )
  }

  def sendToChannel(link: String): Unit = {

    val data = Seq("chat_id" -> BotWrapper.channelId, "text" -> link)
    try {
      val response = BotWrapper.request.postForm(data).asString
    } catch  {
      case f: SocketTimeoutException => log.error("ReadTimeout exception. Check proxy settings")
      case f: TimeoutException => log.error("Timeout exception. Check proxy settings")
      case e => throw e
    }
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

