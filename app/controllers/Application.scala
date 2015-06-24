package controllers

import play.api._
import play.api.mvc._
import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd._
import org.apache.spark.mllib.classification.NaiveBayes
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.mllib.classification.NaiveBayesModel
import util.Global._;
import play.api.libs.json._
import play.api.libs.ws.WS
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.Play.current
import scala.collection.mutable.ListBuffer
import com.sun.syndication.feed.synd.{SyndContent, SyndEntry, SyndFeed}
import com.sun.syndication.io.{SyndFeedInput, XmlReader}
import java.net.URL
import java.util.{List => JList}
import scala.collection.JavaConverters._
import scala.concurrent.Future


object Application extends Controller {
  
  def index = Action {
    Ok(views.html.index("Accuracy = %"))
  }
  
  	
 
def feedTitle(company:String) = Action {
 
    val newsFeed = getTwitterSentiment();	
    Ok("Feed title: " + newsFeed)
    }
    
def twitterFeed(company:String) = Action.async {
 
    val url = "http://api.nytimes.com/svc/search/v2/articlesearch.json?q=apple&fq=apple&begin_date=20150610&end_date=20150611&fl=lead_paragraph,abstract,headline,news_desk,byline&api-key=4696eab1bd48a3f435314cb15624e7be%3A12%3A72139297"
    /*val feed: SyndFeed = new SyndFeedInput().build(new XmlReader(new URL(url)))
    val rss_entries = feed.getEntries.asInstanceOf[JList[SyndEntry]].asScala
    var des = new ListBuffer[String]()	
    for(entry <- rss_entries){
        val description = entry.getDescription().getValue().trim();
        if(!description.isEmpty){
	        des += entry.getDescription().getValue();
        }
	}*/	
	var futureResult:ListBuffer[JsObject] = new ListBuffer[JsObject]();
	WS.url(url).get().map { response =>
         println(response.json)
         val array = (response.json \ "docs").as[Option[JsArray]]
         println(array.toString())
            	    
        Ok("thanks")    	    
	}
	
	
    //Ok("Feed title: " + futureResult.toString())
    }
    


}
