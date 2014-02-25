/*
 * Copyright 2013 David Crosson
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.janalyse.jmx

import com.typesafe.scalalogging.slf4j.Logging

import javax.management.ObjectName
import javax.management.MBeanInfo
import javax.management.MBeanAttributeInfo
import javax.management.RuntimeMBeanException
import java.rmi.UnmarshalException

import scala.collection.JavaConversions._


case class RichMBean(
  objectName: ObjectName,
  mbeanInfoGetter: () => MBeanInfo,
  attributeGetter: (String) => Option[Object],
  attributeSetter: (String, Any) => Unit,
  operationCaller: (String, Array[Any]) => Option[Any]) {
  
  lazy val mbeanInfo = mbeanInfoGetter()
  val name = objectName.toString()
  val domain = objectName.getDomain()
  val keys = mapAsScalaMap(objectName.getKeyPropertyList())
  
  lazy val attributesMap: Map[String, RichAttribute] = {
    try {
      val attrsInfo: List[MBeanAttributeInfo] = mbeanInfo.getAttributes().toList
      val richattrs = attrsInfo map { ai =>
        val n = ai.getName()
        //val d = if (ai.getDescription() == ai.getName || ai.getDescription().trim.size == 0) None else Some(ai.getDescription())
        val d = Option(ai.getDescription()).map(_.trim).filterNot(_.size==0).filterNot(_ == n)
        val a: RichAttribute = ai.getType() match {
          case "java.lang.Boolean" | "boolean" | "java.lang.boolean" => RichBooleanAttribute(n, d)
          case "java.lang.Byte" | "byte" => RichByteAttribute(n, d)
          case "java.lang.Short" | "short" => RichShortAttribute(n, d)
          case "java.lang.Integer" | "int" => RichIntAttribute(n, d)
          case "java.lang.Long" | "long" => RichLongAttribute(n, d)
          case "java.lang.Float" | "float" => RichFloatAttribute(n, d)
          case "java.lang.Double" | "double" => RichDoubleAttribute(n, d)
          case "java.lang.String" | "String" => RichStringAttribute(n, d)
          //case "java.util.List" =>
          //case "java.util.Properties" =>
          case "[Ljava.lang.String;" => RichStringArrayAttribute(n, d)
          //case "javax.management.openmbean.TabularData"      =>
          case "javax.management.openmbean.CompositeData" | "javax.management.openmbean.CompositeDataSupport" => RichCompositeDataAttribute(n, d)
          //case "[Ljavax.management.openmbean.CompositeData;" =>
          //case "[Ljavax.management.ObjectName;" => 
          case x =>
            //println("Warning: Not supported jmx attribute value type %s for %s mbean %s".format(x, n, name))
            //println(x)
            RichGenericAttribute(ai.getName(), d)
        }
        n -> a
      }
      richattrs.toMap
      //def apply[A](attrname:String) = jmx.rmiConnection.getAttribute(objectName,attrname,null).asInstanceOf[A]
    } catch {
      case e:Exception =>
        new Logging() {
          //e.printStackTrace
          logger.warn("Couln't build attributes map for MBean %s (%s)".format(name, e.getMessage))
        }
        Map.empty[String, RichAttribute]
    }
  }

  /*def get[A](name: String): Option[A] = {
    val attrsInfo: List[javax.management.MBeanAttributeInfo] = mbeanInfo.getAttributes().toList
    (attrsInfo find { attrInfo => attrInfo.getName == name }) match {
      case None => None
      case Some(attrInfo) => apply[A](name) match {
        case x if (x == null) => None
        case x => Some(x)
      }
    }
  }*/
  def get[A](name: String): Option[A] = attributeGetter(name).map(_.asInstanceOf[A])

  def apply[A](attrname: String) = attributeGetter(attrname).map(_.asInstanceOf[A]).get
  def set(attrname: String, value: Any) { attributeSetter(attrname, value) }
  def apply[A](attrnames: String*): List[A] = attrnames.toList map { apply[A](_) }
  def call[A](operation: String, args: Any*): Option[A] = operationCaller(operation, args.toArray[Any]).map(_.asInstanceOf[A])

  private def genericGetter[T, R <: RichAttribute](attr: R, getter: (Object) => T): Option[T] = {
    try {
      attributeGetter(attr.name).map(getter(_))
    } catch {
      case e: RuntimeMBeanException if e.getCause().isInstanceOf[UnsupportedOperationException] => None
      case e: UnmarshalException => None
      case e: java.rmi.ConnectException => throw e
      case x: Exception =>
        //println("Warning: Error while getting value for attribute %s mbean %s (%s)".format(attr.name, name, x))
        None
    }
  }

  def getString(attr: RichAttribute): Option[String] = genericGetter(attr, attr.asString)

  def getDouble(attr: RichNumberAttribute): Option[Double] = genericGetter(attr, attr.asDouble)

  def getLong(attr: RichNumberAttribute): Option[Long] = genericGetter(attr, attr.asLong)

  def getInt(attr: RichNumberAttribute): Option[Int] = genericGetter(attr, attr.asInt)

  def getComposite(attr: RichCompositeDataAttribute): Option[Map[String, Object]] = genericGetter(attr, attr.asMap)

  def getNumberComposite[N >: Number](attr: RichCompositeDataAttribute): Option[Map[String, N]] = genericGetter(attr, attr.asNumberMap[N])

  def getString(attrname: String): Option[String] = attributesMap.get(attrname).flatMap(getString(_))

  def getDouble(attrname: String): Option[Double] = attributesMap.get(attrname).collect({ case x: RichNumberAttribute => x }).flatMap(getDouble(_))

  def getLong(attrname: String): Option[Long] = attributesMap.get(attrname).collect({ case x: RichNumberAttribute => x }).flatMap(getLong(_))

  def getInt(attrname: String): Option[Int] = attributesMap.get(attrname).collect({ case x: RichNumberAttribute => x }).flatMap(getInt(_))

  def getComposite(attrname: String): Option[Map[String, Object]] = attributesMap.get(attrname).collect({ case x: RichCompositeDataAttribute => x }).flatMap(getComposite(_))

  def getNumberComposite[N >: Number](attrname: String): Option[Map[String, N]] = attributesMap.get(attrname).collect({ case x: RichCompositeDataAttribute => x }).flatMap(getNumberComposite(_))

  def attributes(): List[RichAttribute] = attributesMap.values.toList
  
  def attributesNames() : List[String] = attributesMap.values.toList.map(_.name)
}
