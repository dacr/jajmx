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

import javax.management.remote.JMXServiceURL

case class JMXOptions(
  host: String = "localhost",
  port: Int = 1099,
  url: Option[JMXServiceURL] = None,
  name: Option[String] = None,
  username: Option[String] = None,
  password: Option[String] = None,
  connectTimeout: Long = 30000,
  retryCount: Int = 5,
  retryDelay: Long = 2000) {
  val credentials: Option[Credentials] = (username, password) match {
    case (None, None)       => None
    case (None, _)          => None
    case (Some(u), Some(p)) => Some(Credentials(u, p))
    case (Some(u), None)    => Some(Credentials(u, ""))
  }
}

object JMXOptions {
  def apply(url: JMXServiceURL): JMXOptions = 
    new JMXOptions(url.getHost, url.getPort, url = Some(url))

  def apply(url: JMXServiceURL, username: String, password: String): JMXOptions = 
    new JMXOptions(url.getHost, url.getPort, url = Some(url), username = Some(username), password = Some(password))
  
}
