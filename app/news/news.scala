package news;
import play.api.libs.json._
import com.sun.syndication.feed.synd.{SyndContent, SyndEntry, SyndFeed}
import com.sun.syndication.io.{SyndFeedInput, XmlReader}
import twitter.Sentiment
case class News(id: String, title: String, link: String, description:String="ab") {


  def words: List[String] = Sentiment.words(description)

  //lazy val passion: Int = Sentiment.passion(text)

  lazy val sentiment: Int = Sentiment.sentiment(description)

  //lazy val emotion = Emotion(passion = passion, sentiment = sentiment)
}

object News {
  implicit val _ = Json.format[News]


 def from(t: SyndEntry): Option[News] = {
    for {
      description <- Option(t.getDescription.getValue).orElse(Some(t.getTitle))
    } yield {
      News(id = java.util.UUID.randomUUID.toString,
        title = t.getTitle, 
        link = t.getLink,
        description = description)
    
  }
}
}
