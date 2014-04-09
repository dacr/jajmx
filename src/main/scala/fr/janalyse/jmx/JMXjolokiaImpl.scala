package fr.janalyse.jmx

import com.typesafe.scalalogging.slf4j.Logging
import javax.management.ObjectName
import org.apache.http.client._
import org.apache.http.client.methods.HttpGet
import org.apache.http.auth._
import org.apache.http.util.EntityUtils
import org.apache.http.impl.client.CloseableHttpClient
import org.json4s._
import org.json4s.native.JsonMethods._
//import org.json4s.JsonAST._
import org.json4s.JsonDSL._


class JMXjolokiaImpl(
  getHttpClient: () => CloseableHttpClient,
  val baseUrl: String,
  val options: Option[JMXOptions] = Some(JMXOptions(host = "localhost", port = 8080, contextbase = Some("/jolokia"))),
  additionalCleaning: Option[() => Any] = None) extends JMX with Logging {

  val httpclient = getHttpClient()

  def close() = {
    try {
      httpclient.close()
    } catch {
      case e: Exception => logger.warn("Exception while closing http connection, let's ignore and continue..." + e.getMessage)
    }
    try {
      additionalCleaning.foreach { _() }
    } catch {
      case e: Exception => logger.warn("Exception in additionnal cleaning procesure, let's ignore and continue..." + e.getMessage)
    }
  }

  def httpGet(rq: String): Tuple2[Int, JValue] = {
    val httpget = new HttpGet(baseUrl +rq)
    val response = httpclient.execute(httpget)
    try {
      val rc = response.getStatusLine().getStatusCode()
      val entity = response.getEntity
      val content = io.Source.fromInputStream(entity.getContent).getLines().mkString("\n")
      EntityUtils.consume(entity)
      (rc, parse(content))
    } finally {
      response.close()
    }
  }
  

  def exists(name: String): Boolean = {
    val (rc, content) = httpGet("")
    ???
  }
  
  def domains: List[String] = {
    val (rc, js) = httpGet("/list?maxDepth=1")
    val values = (js \ "value").values
    for { 
      JObject(entries) <- (js \ "value")
      (domain,_) <- entries
      } yield domain
  }
  
  def names():List[String] = {
    val (rc, js) = httpGet("/list?maxDepth=2")
    for { JObject(entries) <- (js \ "value") ; (name,_) <- entries} yield name
  }
  
  def mbeans(): List[RichMBean] = {
    ???
  }
  
  def mbeans(query: String): List[RichMBean] = ???
  def apply(name: String): RichMBean = ???

  private def mbeanInfoGetter(objectName: ObjectName) = ???
  private def attributeGetter(objectName: ObjectName, attrname: String) = ???
  private def attributeSetter(objectName: ObjectName, attrname: String, attrvalue: Any) {
  }
  private def operationCaller(objectName: ObjectName, operationName: String, args: Array[Any]): Option[Any] = ???

  private def newMBean(objectName: ObjectName) =
    RichMBean(
      objectName,
      () => mbeanInfoGetter(objectName),
      (attrname) => attributeGetter(objectName, attrname),
      (attrname, attrval) => attributeSetter(objectName, attrname, attrval),
      (operationName, args) => operationCaller(objectName, operationName, args))

}
