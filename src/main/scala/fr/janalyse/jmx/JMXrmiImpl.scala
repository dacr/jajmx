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

import javax.management.remote.rmi.RMIConnection
import javax.management.ObjectName

import scala.collection.JavaConversions._


private class JMXrmiImpl(rmiConnection: RMIConnection, val options: Option[JMXOptions] = None) extends JMXJsr160 {

  def close() = try {
    rmiConnection.close
  } catch {
    case e:Exception => logger.error("Exception while closing rmi connection, let's ignore and continue...")
  }
  
  def getMBeanInfo(objectName: ObjectName) = rmiConnection.getMBeanInfo(objectName, null)
  
  def getAttribute(objectName: ObjectName, attrname: String) = {
    val res = rmiConnection.getAttribute(objectName, attrname, null)
    //Option(convert(res))
    Option(res)
  }
  
  def setAttribute(objectName: ObjectName, attrname: String, attrvalue: Any) {
    val attribute = new javax.management.Attribute(attrname, attrvalue)
    rmiConnection.setAttribute(objectName, attribute, null)
  }
  def invoke(objectName: ObjectName, operationName: String, args: Array[Any]): Option[Any] = {
    Option(rmiConnection.invoke(objectName, operationName, args, buildOperationSignature(args), null))
  }
  private def newMBean(objectName: ObjectName) =
    RichMBean(
      objectName,
      () => getAttributesMap(objectName),
      (attrname) => getAttribute(objectName, attrname),
      (attrname, attrval) => setAttribute(objectName, attrname, attrval),
      (operationName, args) => invoke(objectName, operationName, args)
    )

  def domains: List[String] = rmiConnection.getDomains(null).toList
  def names(query:String):List[String]=rmiConnection.queryNames(null, string2objectName(query),null).toList.map(_.getCanonicalName())
  override def names():List[String]=rmiConnection.queryNames(null, null,null).toList.map(_.getCanonicalName())  
  def exists(name: String): Boolean = rmiConnection.queryNames(name, null, null).size > 0
  def apply(name: String): RichMBean = newMBean(name)
  def mbeans(query: String): List[RichMBean] = rmiConnection.queryNames(null, string2objectName(query), null).toList map { newMBean(_) }
  override def mbeans(): List[RichMBean] = rmiConnection.queryNames(null, null, null).toList map { newMBean(_) }
}

