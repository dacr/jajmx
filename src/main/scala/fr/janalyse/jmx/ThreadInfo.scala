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

import javax.management.openmbean.CompositeData

case class ThreadInfo(
  name: String,
  id: Long,
  status: String,
  stack: List[StackEntry],
  lock: Option[Lock],
  blockedCount: Long,
  waitedCount: Long,
  serviceName: String)

object ThreadInfo {
  def apply(serviceName: String, ti: CompositeData) = {
    val id = ti.get("threadId").toString.toLong
    val name = ti.get("threadName").toString
    val state = ti.get("threadState").toString
    val blockedCount = ti.get("blockedCount").toString.toLong
    val waitedCount = ti.get("waitedCount").toString.toLong
    val stack = ti.get("stackTrace").asInstanceOf[Array[CompositeData]] map { stackitem =>
      val className = stackitem.get("className").asInstanceOf[String]
      val fileName = stackitem.get("fileName").asInstanceOf[String]
      val lineNumber = stackitem.get("lineNumber").asInstanceOf[Int]
      val methodName = stackitem.get("methodName").asInstanceOf[String]
      val nativeMethod = stackitem.get("nativeMethod").asInstanceOf[Boolean]
      StackEntry(
          className = className,
          fileName = fileName,
          lineNumber = lineNumber,
          methodName = methodName,
          nativeMethod = nativeMethod)
    }
    val lock = Option(ti.get("lockName")) map { _.toString } filter { _.size > 0 } map { lockName =>
      val lockOwnerId = Option(ti.get("lockOwnerId")) map { _.asInstanceOf[Long] } filterNot { _ == -1 }
      val lockOwnerName = Option(ti.get("lockOwnerName")) map { _.toString.trim }
      Lock(lockName, lockOwnerId, lockOwnerName, serviceName)
    }
    new ThreadInfo(
        name,
        id,
        state,
        stack.toList,
        lock,
        blockedCount,
        waitedCount,
        serviceName)
  }
}
