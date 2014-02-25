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

import scala.collection.JavaConversions._

import javax.management.openmbean.TabularDataSupport
import javax.management.openmbean.TabularData
import javax.management.openmbean.CompositeDataSupport
import javax.management.openmbean.CompositeData

case class CompositeDataWrapper[CDT <: CompositeData](cd: CDT) {
  lazy val content: Map[String, Object] = {
    val keys = cd.getCompositeType().keySet()
    val tuples = keys.toList map { k => (k -> cd.get(k)) }
    tuples.toMap
  }
  def toWrapper() = this
  //def get(key:String):Option[Object] = content.get(key)
  def getString(key: String): Option[String] = content.get(key) map { _.toString }
  def get[A](key: String): Option[A] = content.get(key) map { _.asInstanceOf[A] }

  // TODO Improve number support and add more typed getter for basic types
  def getNumber[N >: Number](key: String): Option[N] = {
    for (raw <- content.get(key)) yield {
      raw match {
        case e: java.lang.Byte => e.toDouble
        case e: java.lang.Short => e.toDouble
        case e: java.lang.Integer => e.toDouble
        case e: java.lang.Long => e.toDouble
        case e: java.lang.Float => e.toDouble
        case e: java.lang.Double => e
        case e: java.lang.String => e.toDouble
      }
    }
  }
}

case class TabularDataWrapper[TD <: TabularData](tabularData: TD) {
  lazy val content: Map[String, Map[String, Object]] = {
    val indexNamesInCell = tabularData.getTabularType().getIndexNames()
    val tuples = tabularData.values collect {
      case v: CompositeDataSupport =>
        val cellContent = v.content
        val key = indexNamesInCell map { cellContent.get(_).get } mkString ("-")
        val submap = cellContent filterNot { case (k, _) => indexNamesInCell contains k }

        key -> submap

      case (k /*: java.util.List[String]*/ , v: CompositeData) =>
        val cellContent = v.content
        val key = indexNamesInCell map { cellContent.get(_).get } mkString ("-")
        val submap = cellContent filterNot { case (k, _) => indexNamesInCell contains k }

        key -> submap
    }
    tuples.toMap
  }
  def toWrapper() = this
  def get(key: String): Option[Map[String, Object]] = content.get(key)
  def get(key: String, subkey: String): Option[Object] = content.get(key) flatMap { _.get(subkey) }
  def getString(key: String, subkey: String): Option[String] = get(key, subkey) map { _.toString }
}
