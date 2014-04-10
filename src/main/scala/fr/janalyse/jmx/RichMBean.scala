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
  attributesMapGetter: () => Map[String, RichAttribute],
  attributeGetter: (String) => Option[Object],
  attributeSetter: (String, Any) => Unit,
  operationCaller: (String, Array[Any]) => Option[Any]) extends Logging {
 
  override def toString() = name
  
  val name = objectName.toString()
  val domain = objectName.getDomain()
  val keys = mapAsScalaMap(objectName.getKeyPropertyList())

  lazy val attributesMap: Map[String, RichAttribute] = attributesMapGetter()

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
      case e: java.net.ConnectException => throw e
      case e: java.net.SocketException => throw e
      case x: Exception =>
        logger.error(s"Warning: Error while getting value for attribute ${attr.name} mbean $name", x)
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
