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
package fr.janalyse

import scala.collection.JavaConverters._

import javax.management.ObjectName
import javax.management.ObjectInstance
import java.rmi.MarshalledObject
import javax.management.openmbean.CompositeData
import javax.management.openmbean.CompositeDataSupport
import javax.management.openmbean.TabularData
import javax.management.openmbean.TabularDataSupport

package object jmx {


  implicit def string2objectName(name: String): ObjectName = new ObjectName(name)
  implicit def objectInstance2ObjectName(that: ObjectInstance): ObjectName = that.getObjectName
  //implicit def objectName2RichMBean(that:ObjectName)(implicit ajmx:JMX) = RichMBean(that)
  //implicit def string2RichMBean(that:String)(implicit ajmx:JMX):RichMBean = RichMBean(that)
  implicit def toMarshalledObject[A](that: A): MarshalledObject[A] = new MarshalledObject[A](that)

  implicit def compositeDataAsScalaWrapper[CD <: CompositeData](cd: CD): CompositeDataWrapper[CD] = CompositeDataWrapper(cd)
  //implicit def compositeDataSupportAsScalaWrapper(cd : CompositeDataSupport): CompositeDataSupportWrapper = CompositeDataSupportWrapper(cd)
  implicit def tabularDataAsScalaWrapper[TD <: TabularData](td: TD): TabularDataWrapper[TD] = TabularDataWrapper(td)
  //implicit def tubularDataSupportAsScalaWrapper(td:TabularDataSupport):TabularDataSupportWrapper = TabularDataSupportWrapper(td)

}