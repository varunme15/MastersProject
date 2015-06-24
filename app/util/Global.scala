package util

import play.api._
import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd._
import org.apache.spark.mllib.classification.NaiveBayes
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.classification.NaiveBayesModel
import org.apache.spark.mllib.regression.LabeledPoint
import play.api.libs.json.JsValue
import org.apache.spark.streaming._
import org.apache.spark.streaming.StreamingContext._
import org.apache.spark.streaming.dstream._
import scala.io.Source
import org.apache.spark.streaming.twitter._
import play.api.libs.json._
import _root_.twitter._
import _root_.twitter.Sentiment
import org.apache.spark.serializer.KryoSerializer
import com.sun.syndication.feed.synd.{SyndContent, SyndEntry, SyndFeed}
import com.sun.syndication.io.{SyndFeedInput, XmlReader}
import java.net.URL
import java.util.{List => JList}
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Map
import news.News

object Global extends GlobalSettings {
      
       val conf = new SparkConf() // skip loading external settings
      .setMaster("local[4]") // run locally with enough threads
      .setAppName("healthSystem")
      .set("spark.logConf", "true")
      .set("spark.driver.host", "localhost")
  
      val sc = new SparkContext(conf)
     
	val ssc = new StreamingContext(sc,Seconds(10));
	System.setProperty("twitter4j.oauth.consumerKey","qdi8tvrl4ps5vZwZ7TFjqlc2p");
    System.setProperty("twitter4j.oauth.consumerSecret","YFer7Y9yXsgnMf3UqdNslNOJ2LHqmeIO67h0lunqQxtzUhrpHo");
    System.setProperty("twitter4j.oauth.accessToken","113585904-j6otVP6U2CW0TI2j2yNPz9ouWGoVMA81HgD46GAl");
    System.setProperty("twitter4j.oauth.accessTokenSecret","JCTkW7VASKAUgRemF4cTZezn7ZxHgTBhanTXn5yVN3qGe");
      
def getTwitterStream() = {  
    val stream = TwitterUtils.createStream(ssc, None)
    val mapTwitterStream = stream.flatMap(Tweet.from(_))
    

    val rawStockStream = mapTwitterStream.filter(!_.retweet) //.filter(_.countryCode.nonEmpty)
    val rawStockIdTweetJson: DStream[(String, String)] = rawStockStream.map(t => t.id -> Json.toJson(t).toString)
    val StockIdTweetStream = rawStockIdTweetJson;
    val StockTweetJson = StockIdTweetStream.map(_._2)
    //StockTweetJson.print()
    val bufferedTweetStream: DStream[Tweet] = StockIdTweetStream.flatMap {
          case (id, json) => Json.parse(json).asOpt[Tweet]
        }
    bufferedTweetStream
}

def sentimentByWord() = {
    // Input lines with TSV data with word <TAB> sentiment scores (-5 <= sentiment score <= 5)
    val sentimentLines = sc.textFile("/home/varun/devSpace/TwitterSentimentAnalyzer/src/main/resources/words.txt");

    // (Word, Sentiment) tuples, created from the lines of TSV data
    val sentimentByWord = sentimentLines.map { line =>
      val tokens = line.split('\t')
      (tokens(0), tokens(1).toInt)
    }
    sentimentByWord.cache()
    sentimentByWord
  }
  
  def tweetsBySentiment(tweets: RDD[Tweet], sentimentByWord: RDD[(String, Int)], easyWay: Boolean = false): RDD[(Int, Tweet)] = {
    if (easyWay) {
      // Compute the sentiment directly in memory (rather than distributing the calculation to Spark)
      tweets.map { tweet: Tweet =>
        (tweet.sentiment, tweet)
      }
    } else {
      // (tweetId -> Tweet) - we will later join with this, so partition it explicitly and persist it
      val tweetsById = tweets.filter(_.lang !="en")
        .map { tweet => tweet.id -> tweet }
        .partitionBy(new org.apache.spark.HashPartitioner(20))
        .persist()

      // ( word -> tweetId )
      val tweetWordsById = tweetsById
        .values // Use the persisted tweetsById and extract values rather than re-calculating original tweets RDD
        .flatMap { tweet => tweet.words.map(_ -> tweet.id) }

      // ( word -> (tweetId -> sentimentScore) )
      val joinedWords = tweetWordsById.join(sentimentByWord)

      // ( tweetId -> sentimentScore )
      val tweetIdToSentiment = joinedWords
        .map(_._2)
        .reduceByKey(_ + _)

      // ( tweetId -> ( sentimentScore -> Tweet ) )
      val joinedSentimentTweets = tweetIdToSentiment.join(tweetsById)

      // ( sentimentScore -> Tweet )
      val sentimentTweets = joinedSentimentTweets
        .map(_._2)
        .persist()
   
      sentimentTweets
    }
  } 

  def newsBySentiment(news: RDD[News], sentimentByWord: RDD[(String, Int)], easyWay: Boolean = false): RDD[(Int, News)] = {
    if (easyWay) {
      // Compute the sentiment directly in memory (rather than distributing the calculation to Spark)
      news.map { news: News =>
        (news.sentiment, news)
      }
    } else {
      // (tweetId -> Tweet) - we will later join with this, so partition it explicitly and persist it
      val newsById = news
        .map { news => news.id -> news }
        .partitionBy(new org.apache.spark.HashPartitioner(20))
        .persist()
    
      println("newsByID count  :"+newsById.count() )
      // ( word -> tweetId )
      val newsWordsById = newsById
        .values // Use the persisted tweetsById and extract values rather than re-calculating original tweets RDD
        .flatMap { news => news.words.map(_ -> news.id) }
        
      // ( word -> (tweetId -> sentimentScore) )
      val joinedWords = newsWordsById.join(sentimentByWord)

      // ( tweetId -> sentimentScore )
      val newsIdToSentiment = joinedWords
        .map(_._2)
        .reduceByKey(_ + _)

      // ( tweetId -> ( sentimentScore -> Tweet ) )
      val joinedSentimentNews = newsIdToSentiment.join(newsById)

      // ( sentimentScore -> Tweet )
      val sentimentNews = joinedSentimentNews
        .map(_._2)
        .persist()
   
      sentimentNews
    }
  } 

 def getNewsFeed(company:String):RDD[News] = {
	
	val url = "http://finance.yahoo.com/rss/headline?s="+company+""
    val feed: SyndFeed = new SyndFeedInput().build(new XmlReader(new URL(url)))
    val rss_entries = feed.getEntries.asInstanceOf[JList[SyndEntry]].asScala
    var newsEntry = new ListBuffer[News]()	
    for(entry <- rss_entries){
        News.from(entry).foreach(entry => { newsEntry+=entry})
	}
	println(newsEntry.toString())
    val newsRdd = sc.parallelize(newsEntry);
    return newsRdd;
 }
 
 def getNewsSentiment(company:String) = {
   val newsRDD = getNewsFeed(company);
   println("count:--------------------- "+newsRDD.count());
   val sentimentWord = sentimentByWord();
   val newsSentimentRDD = newsBySentiment(newsRDD, sentimentWord, false)
   println("newsSentimentRDD count:--------------------- "+newsSentimentRDD.count());
   var finalscore = 0;
   newsSentimentRDD.foreach { case(score,news) =>{
     finalscore +=  score;
     println("finalscore "+ finalscore)
   } 
   println("finalscore 1:"+ finalscore)
   }
   println("finalscore 2:"+ finalscore)
   
 }
  
def getTwitterSentiment():Int = {
    val TwitterRDD = getTwitterStream();
    val sentimentWord = sentimentByWord();
    val StockTweetSentimentStream = TwitterRDD.transform { tweets => tweetsBySentiment(tweets, sentimentByWord) }
    var finalscore = 0;
    //val tweetSentimentRDD = tweetsBySentiment(TwitterRDD, sentimentWord, false)
    StockTweetSentimentStream.foreachRDD( rdd => {
        rdd.foreach{
            case(score,tweet) =>{
            finalscore +=  score;
            println(score)
            }
        }
    })
   println("finalscore 1:"+ finalscore)
   return finalscore;
   }


override def onStart(app: Application) {

        Logger.info("Application has started...");
    }
}
