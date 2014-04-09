package fr.janalyse.jmx

import com.typesafe.scalalogging.slf4j.Logging
import javax.management.ObjectName

import org.jolokia.client._
import org.jolokia.client.request._


class JMXjolokiaImpl(
  val url: String,
  val options: Option[JMXOptions] = Some(JMXOptions(host = "localhost", port = 8080, contextbase = Some("/jolokia"))),
  additionalCleaning: Option[() => Any] = None) extends JMX with Logging {

  val j4p = new J4pClient(url)

  def close() = {
    try {
      j4p.getHttpClient().getConnectionManager().shutdown()
    } catch {
      case e: Exception => logger.warn("Exception while closing http connection, let's ignore and continue..." + e.getMessage)
    }
    try {
      additionalCleaning.foreach { _() }
    } catch {
      case e: Exception => logger.warn("Exception in additionnal cleaning procesure, let's ignore and continue..." + e.getMessage)
    }
  }


  def exists(name: String): Boolean = ??? // j4p.execute(new J4pReadRequest(name))
  def domains: List[String] = ???
  def mbeans(query: String): List[RichMBean] = ???
  def mbeans(): List[RichMBean] = ??? // j4p.execute(new J4pListRequest())
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
      (operationName, args) => operationCaller(objectName, operationName, args)
    )

}
