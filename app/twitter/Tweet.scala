package twitter;
import play.api.libs.json._

case class Tweet(id: String, text: String, retweet_count: Long, followerCount:Long, lang:String, hashtags: Set[String], retweet: Boolean, countryCode: String = "", stateCode: String = "", city: String = "") {

  def hasLocation: Boolean = countryCode.nonEmpty && stateCode.nonEmpty && city.nonEmpty

  def words: List[String] = Sentiment.words(text)

  lazy val passion: Int = Sentiment.passion(text)

  lazy val sentiment: Int = Sentiment.sentiment(text)

  lazy val emotion = Emotion(passion = passion, sentiment = sentiment)
}

object Tweet {

  implicit val _ = Json.format[Tweet]

  def cityState(placeType: String, placeName: String, placeFullName: String): (String, String) = {
    placeFullName.split("""\s*,\s*""").toList match {
      case city :: stateCode :: Nil if city == placeName && placeType == "city" =>
        (city, stateCode)
      case _ =>
        ("", "")
    }
  }

  /**
   * Create a tweet from a string encoded in Twitter JSON format
   */
  def from(jsonStr: String): Option[Tweet] = {
    val tweet = Json.parse(jsonStr).asOpt[JsObject].flatMap { jsObj =>
      for {
        id <- (jsObj \ "id_str").asOpt[String]
        text <- (jsObj \ "text").asOpt[String]
        retweeted <- (jsObj \ "retweeted").asOpt[Boolean]
        retweet_count <- (jsObj \ "retweet_count").asOpt[Int]
        countryCode <- (jsObj \ "place" \ "country_code").asOpt[String]
        placeType <- (jsObj \ "place" \ "place_type").asOpt[String]
        fullName <- (jsObj \ "place" \ "full_name").asOpt[String]
        followerCount <- (jsObj \ "user" \ "followers_count").asOpt[Int]
        lang <- (jsObj \ "user" \ "lang").asOpt[String]
        name <- (jsObj \ "place" \ "name").asOpt[String]
      } yield {
        val hashtags = (jsObj \ "entities" \ "hashtags" \\ "text").map(_.asOpt[String].getOrElse("")).filter(_.nonEmpty).toSet
        val (city, stateCode) = cityState(placeType, name, fullName)
        Tweet(id = id, text = text, retweet_count = retweet_count, followerCount = followerCount, lang = lang, hashtags = hashtags, retweet = retweeted, countryCode = countryCode, stateCode = stateCode, city = city)
      }
    }
    tweet
  }

  def from(t: twitter4j.Status): Option[Tweet] = {
    for {
      place <- Option(t.getPlace)
      placeType <- Option(place.getPlaceType).orElse(Some(""))
      placeName <- Option(place.getName).orElse(Some(""))
      placeFullName <- Option(place.getFullName).orElse(Some(""))
      countryCode <- Option(place.getCountry).orElse(Some(""))
    } yield {
      val (city, stateCode) = cityState(placeType, placeName, placeFullName)
      Tweet(id = t.getId.toString,
        text = t.getText, 
        retweet_count = t.getRetweetCount,
        followerCount = t.getUser.getFollowersCount,
        lang = t.getUser.getLang,
        hashtags = t.getHashtagEntities.toSeq.map(_.getText).toSet,
        retweet = t.isRetweet,
        city = city,
        stateCode = stateCode,
        countryCode = countryCode)
    }
  }

}