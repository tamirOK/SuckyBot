
import java.net.{InetSocketAddress, Proxy}

import scalaj.http._
import info.mukel.telegrambot4s.clients.ScalajHttpClient
import info.mukel.telegrambot4s.api.declarative.Commands
import info.mukel.telegrambot4s.api.{Polling, TelegramBot}
import info.mukel.telegrambot4s.methods.ParseMode.ParseMode
import info.mukel.telegrambot4s.methods.SendMessage
import info.mukel.telegrambot4s.models.ChatId.Channel
import info.mukel.telegrambot4s.models.{Message, ReplyMarkup}
import org.json4s.JsonAST._
import org.json4s.jackson.JsonMethods.parse

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration


class ImgurBot(val token: String) extends TelegramBot with Polling with Commands {

  case class Image(link: String)

  case class Post(link: String, images: List[JValue])

  val proxy = new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("38.96.9.236", 8008))
  override val client = new ScalajHttpClient(token, proxy)

  def replyToChannel(text                  : String,
                     parseMode             : Option[ParseMode] = None,
                     disableWebPagePreview : Option[Boolean] = None,
                     disableNotification   : Option[Boolean] = None,
                     replyToMessageId      : Option[Int] = None,
                     replyMarkup           : Option[ReplyMarkup] = None)
                    (implicit message: Message): Future[Message] = {
    request(
      SendMessage(
        Channel("@nobodyneedthatshit"),
        text,
        parseMode,
        disableWebPagePreview,
        disableNotification,
        replyToMessageId,
        replyMarkup
      )
    )
  }

  onCommand("/start") {

    implicit msg => {
      var response = Http("https://api.imgur.com/3/gallery/hot/").header("Authorization", "Client-ID b5321b8f5cb0519").asString.body
      val ast = parse(response)

      val data: List[Post] = for {
        JObject(item) <- ast
        JField("link", JString(link)) <- item
        JArray(images) <- ast \\ "images"
      } yield Post(link, images)

      val links = for {
        post <- data
        JObject(image) <- post.images
        JField("link", JString(link)) <- image
      } yield link
      links.slice(0, 10).foreach(link => replyToChannel(link))
    }
  }
}

object Main {
  def main(args: Array[String]): Unit = {
    val bot = new ImgurBot("582060096:AAFQy54HegV3MARTDyvU-gVko1z2QE_BQ48")
    val eol = bot.run()
    scala.io.StdIn.readLine()
    bot.shutdown()
    Await.result(eol, Duration.Inf)
  }
}