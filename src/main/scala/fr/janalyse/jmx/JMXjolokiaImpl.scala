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
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity

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

  def httpPost(requests: JValue): Tuple2[Int, JValue] = {
    val httppost = new HttpPost(baseUrl)
    httppost.setEntity(new StringEntity(compact(render(requests))))
    val response = httpclient.execute(httppost)
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

  def esc(path: String): String = {
    path.replaceAll("!", "!!")
      .replaceAll("/", "!/")

  }

  def jread(oname: String, attrs: List[String] = List.empty): JValue = {
    val (rc, js) = attrs match {
      case Nil        => httpPost(("type" -> "read") ~ ("mbean" -> oname))
      case one :: Nil => httpPost(("type" -> "read") ~ ("mbean" -> oname) ~ ("attribute" -> one))
      case _          => httpPost(("type" -> "read") ~ ("mbean" -> oname) ~ ("attribute" -> attrs))
    }
    js
  }

  implicit val formats = org.json4s.DefaultFormats
  def jwrite(oname: String, attr: String, value: Any): JValue = {
    val query =
      ("type" -> "write") ~
        ("mbean" -> oname) ~
        ("attribute" -> attr) ~
        ("value" -> Extraction.decompose(value))
    println(query)
    val (rc, js) = httpPost(query)
    js
  }

  def jsearch(query: String): JValue = {
    val (rc, js) = httpPost(("type" -> "search") ~ ("mbean" -> query))
    js
  }

  def jlist(
    domain: Option[String] = None,
    keys: Option[String] = None,
    maxDepth: Option[Int] = None,
    maxObjects: Option[Int] = None) = {
    val path = domain.map(d => esc(d) + keys.map("/" + esc(_)).getOrElse("")).getOrElse("")
    val params = List(
      maxDepth.map("maxDepth" -> JInt(_)),
      maxObjects.map("maxObjects" -> JInt(_))
    ).flatten
    val query = ("type" -> "list") ~ ("path" -> path) ~ params
    val (rc, js) = httpPost(query)
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

  def names(query: String): List[String] = {
    val js = jsearch(query)
    for { JArray(list) <- js \ "value"; JString(name) <- list } yield name
  }

  def mbeans(query: String): List[RichMBean] = names(query).map(apply)

  def apply(name: String): RichMBean = {
    val objectName = new ObjectName(name)
    newMBean(objectName)
  }

  def getAttributesMetaData(objectName: ObjectName): List[AttributeMetaData] = {
    val js = jlist(Some(objectName.getDomain), Some(objectName.getKeyPropertyListString))
    val value = js \ "value"
    try {
      value \ "attr" match {
        case JObject(attrs) =>
          Some(attrs)
          val attrsInfos = for {
            (name, info) <- attrs
            JObject(entries) <- info
            meta = entries.toMap
          } yield {
            val adesc = meta.get("desc") match {
              case Some(JString(d))   => d
              case Some(JNull) | None => ""
              case x                  => logger.warn(s"Unsupported desc $x for $objectName field $name"); ""
            }
            val atype = meta.get("type") match {
              case Some(JString(t)) => t
              case x                => logger.warn(s"Unsupported type $x for $objectName field $name"); ""
            }
            val rw = meta.get("rw") match {
              case Some(JBool(rw)) => rw
              case x               => logger.warn(s"Unsupported rw $x for $objectName field $name"); false
            }
            AttributeMetaData(name, adesc, atype, rw)
          }
          attrsInfos
        case JNothing =>
          List.empty
      }
    } catch {
      case e: Exception =>
        e.printStackTrace
        List.empty
    }
  }

  def convert(in: JValue):Object = {
    in match {
      case JInt(e)      => e
      case JDouble(e)   => new java.lang.Double(e)
      case JDecimal(e)  => e
      case JString(e)   => e
      case JArray(list) => list.map(convert)
      case JBool(l)     => new java.lang.Boolean(l)
      case JNothing     => null
      case JNull        => null
      case JObject(ts)  => ts.map{case (k, value) => k->convert(value)}.toMap
    }
  }

  def getAttribute(objectName: ObjectName, attrname: String) = {
    val js = jread(objectName.getCanonicalName(), attrname :: Nil)
    val res = convert(js \ "value")
    Some(res)
  }

  def setAttribute(objectName: ObjectName, attrname: String, attrvalue: Any) {
    jwrite(objectName.getCanonicalName(), attrname, attrvalue)
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
