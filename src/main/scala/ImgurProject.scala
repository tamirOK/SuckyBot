import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

import akka.actor._
import com.typesafe.config.ConfigFactory
import org.json4s.JsonAST._
import org.json4s.jackson.JsonMethods.parse
import scalaj.http.{Http, HttpOptions, HttpRequest}

import scala.collection._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class FetchActor extends Actor with ActorLogging {
  val fetchPeriod: FiniteDuration = 60.seconds
  val counter = new AtomicInteger()
  var postPeriod: FiniteDuration = 60.seconds

  override def preStart(): Unit = {
    scheduleTimer()
  }

  def scheduleTimer(): Cancellable = {
    context.system.scheduler.scheduleOnce(
      fetchPeriod, context.self, FetchData
    )
  }

  override def postRestart(reason: Throwable): Unit = {}

  override def receive: PartialFunction[Any, Unit] = {
    case FetchData =>
      log.warning("FetchData message")
      Fetcher.getPosts()
      context.system.scheduler.scheduleOnce(fetchPeriod, context.self, FetchData)
    case r =>
      log.warning(s"Unexpected message: $r")
  }
}

case object FetchData

object Fetcher {
  var lastImage: BigInt = 0

  def getPosts(): Unit = {
    var request = Http("https://api.imgur.com/3/gallery/hot/top/").header("Authorization", "Client-ID b5321b8f5cb0519").option(HttpOptions.connTimeout(10000))
    val response = request.asString
    if (response.code != 200) {
      println("Error: status code != 200!!!")
      return
    }
    val bodyData = parse(response.body)
    val images: List[JValue] = for {
      JObject(item) <- bodyData
      JField("images", JArray(images)) <- item
      img <- images
    } yield img
    var currentLastImage: BigInt = lastImage
    for {
      img <- images
      JObject(imgObject) <- img
      JField("link", JString(link)) <- imgObject
      JField("datetime", JInt(date)) <- imgObject
      if date > lastImage
    } {
      currentLastImage = date.max(currentLastImage)
      sendToChannel(link)
    }
    lastImage = currentLastImage
  }

  def sendToChannel(link: String): Unit = {

    val data = Seq("chat_id" -> BotWrapper.channelId, "text" -> link)
    try {
      val response = BotWrapper.request.postForm(data).asString
    } catch {
      case f: SocketTimeoutException => println("ReadTimeout exception. Check proxy settings")
      case f: TimeoutException => println("Timeout exception. Check proxy settings")
      case e: Throwable => throw e
    }
  }
}

object BotWrapper {
  val postToChannelUrl = "https://api.telegram.org/bot582060096:AAFQy54HegV3MARTDyvU-gVko1z2QE_BQ48/sendMessage"
  val channelId = "@nobodyneedthatshit"
  val request: HttpRequest = Http(postToChannelUrl).option(HttpOptions.followRedirects(true)).option(HttpOptions.connTimeout(5000)).option(HttpOptions.readTimeout(10000)).proxy("38.96.9.236", 8008)

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

