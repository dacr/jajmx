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
import org.json4s.JsonDSL._
import java.net.URLEncoder

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
    val httpget = new HttpGet(baseUrl + rq)
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

  // ============================================================================

  def jescape(str: String) = URLEncoder.encode(str, "US-ASCII")

  def jread(oname: String, attr: Option[String] = None): JValue = {
    val escapedname = jescape(oname)
    val (rc, js) = attr match {
      case None           => httpGet(s"/read/$escapedname")
      case Some(attrname) => httpGet(s"/read/$escapedname/$attrname")
    }
    js
  }

  def jsearch(query: String): JValue = {
    val escapedquery = jescape(query)
    val (rc, js) = httpGet(s"/search/$escapedquery")
    js
  }

  def jlist(
    domain: Option[String] = None,
    path: Option[String] = None,
    maxDepth: Option[Int] = None,
    maxObjects: Option[Int] = None) = {
    val basequery =
      "/list" + domain.map(d => "/" + jescape(d) + path.map("/" + jescape(_)).getOrElse("")).getOrElse("")
    val params = List(
      maxDepth.map(s"maxDepth=" + _),
      maxObjects.map(s"maxObjects=" + _)
    ).flatten
    val (rc, js) = params match {
      case Nil        => httpGet(basequery)
      case paramslist => httpGet(basequery + "?" + paramslist.mkString("&"))
    }
    js
  }

  // ============================================================================

  def exists(name: String): Boolean = {
    val js = jread(name)
    val JInt(status) = js \ "status"
    status.toInt == 200
  }

  def domains: List[String] = {
    val js = jlist(maxDepth = Some(1))
    val values = (js \ "value").values
    for {
      JObject(entries) <- (js \ "value")
      (domain, _) <- entries
    } yield domain
  }

  /*
  def names():List[String] = {
    val js = jlist(maxDepth=Some(2))
    val JObject(ob) = (js \ "value")
    for { JField(domain, JObject(names)) <- ob
          (name,_) <- names
      } yield s"$domain:$name"
  }
   */

  def names(): List[String] = names("*:*")

  def names(query: String): List[String] = {
    val js = jsearch(query)
    for { JArray(list) <- js \ "value"; JString(name) <- list } yield name
  }

  def mbeans(): List[RichMBean] = names.map(apply)

  def mbeans(query: String): List[RichMBean] = names(query).map(apply)

  def apply(name: String): RichMBean = {
    val objectName = new ObjectName(name)
    newMBean(objectName)
  }

  def getAttributesMetaData(objectName: ObjectName): List[AttributeMetaData] = {
    val js = jlist(Some(objectName.getDomain), Some(objectName.getKeyPropertyListString))
    val value = js \ "value"
    try {
	    val JObject(attrs) = value \ "attr"
	    val attrsInfos = for {
	      (name, info) <- attrs
	      JObject(entries) <- info
	      meta = entries.toMap
	      JString(adesc) <- meta.get("desc")
	      JString(atype) <- meta.get("type")
	      JBool(rw) <- meta.get("rw")
	    } yield {
	      AttributeMetaData(name, adesc, atype, rw)
	    }
	    attrsInfos
    } catch {
      case e:Exception =>
        e.printStackTrace
        List.empty
    }
  }

  def getAttribute(objectName: ObjectName, attrname: String) = {
    val js = jread(objectName.getCanonicalName(), Some(attrname))
    val res:Object = (js \ "value") match {
       case JInt(e)     => e
       case JDouble(e)  => new java.lang.Double(e)
       case JDecimal(e) => e
       case JString(e)  => e
       case JArray(l) => l
       case JBool(l) => new java.lang.Boolean(l)
       case JNothing => null
       case JNull => null
       case JObject(o) => o
    }
    Some(res)
  }

  def setAttribute(objectName: ObjectName, attrname: String, attrvalue: Any) {
  }

  def invoke(objectName: ObjectName, operationName: String, args: Array[Any]): Option[Any] = ???

  private def newMBean(objectName: ObjectName) =
    RichMBean(
      objectName,
      () => getAttributesMap(objectName),
      (attrname) => getAttribute(objectName, attrname),
      (attrname, attrval) => setAttribute(objectName, attrname, attrval),
      (operationName, args) => invoke(objectName, operationName, args))

}
