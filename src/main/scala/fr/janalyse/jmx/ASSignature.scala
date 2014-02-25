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

sealed trait ASSignature {
  val release: String
  val home: String
  val domain: String // jmx domain where to find all mbeans or "".
  val family: String // AS family name : jboss, tomcat, jetty, jonas, ...
}

case class TomcatSignature(
  release: String, // tomcat release
  home: String, // tomcat home directory (CATALINA_HOME)
  domain: String = "Catalina" // tomcat JMX Domain
  ) extends ASSignature {
  val family = "tomcat"
}

case class JettySignature(
  release: String,
  home: String,
  domain: String = "") extends ASSignature {
  val family = "jetty"
}

case class WebMethodsSignature(
  release: String, // Webmethod release
  home: String, // Webmethod home directory (WM_HOME)
  domain: String = "" // Not yet used
  // com.webmethods.sc.config.configRoot
  ) extends ASSignature {
  val family = "webmethods"
}

case class JonasSignature(
  release: String, // jonas release
  domain: String, // jonas JMX domain name
  name: String, // jonas Instance name
  home: String, // jonasBase jmx value or jonas.base property value
  root: String // jonasRoot jmx value or jonas.root property value
  ) extends ASSignature {
  val family = "jonas"
}

case class JBossSignature(
  release: String, // jboss release
  home: String, // jboss home directory (WM_HOME)
  domain: String = "jboss.as" // jboss.as
  ) extends ASSignature {
  val family = "jboss"
}

object ASSignature {
  private def getJonasDomain(jmx: JMX) = jmx.domains find { domain =>
    !jmx.mbeans("%s:j2eeType=J2EEServer,*".format(domain)).isEmpty
  }
  private def getJonasName(jmx: JMX, domain: String): Option[String] =
    jmx.mbeans("%s:j2eeType=J2EEServer,*".format(domain)).flatMap(_.keys.get("name")).headOption

  private def lookup4jonasSignature(jmx: JMX): Option[ASSignature] = {
    for (
      domain <- getJonasDomain(jmx);
      name <- getJonasName(jmx, domain);
      j2eeserver <- jmx.get("%s:j2eeType=J2EEServer,name=%s".format(domain, name));
      asname <- j2eeserver.getString("serverName");
      rel <- j2eeserver.getString("serverVersion")
    ) yield {
      lazy val basedirFromEngine = jmx.get("%s:type=Engine".format(domain)) flatMap { _.getString("baseDir") }
      lazy val basedirFromProps = jmx.systemProperties.get("jonas.base")
      val jonasBase = j2eeserver.getString("jonasBase") getOrElse (basedirFromEngine getOrElse basedirFromProps.get) // TODO not 100% safe
      lazy val rootdirFromProps = jmx.systemProperties.get("jonas.root") orElse jmx.systemProperties.get("install.root")
      val jonasRoot = j2eeserver.getString("jonasRoot") getOrElse rootdirFromProps.get // TODO not 100% safe

      JonasSignature(rel, domain, name, jonasBase, jonasRoot)
    }
  }

  private def lookup4jettySignature(jmx: JMX): Option[ASSignature] = {
    val jettydomains = jmx.domains.filter(_.contains("jetty"))
    if (jettydomains.isEmpty) None else {
      val mbs = jettydomains.flatMap(jd => jmx.mbeans(jd + ":*"))
      for (
        mb <- mbs.find(_.attributesNames.contains("version"));
        version <- mb.getString("version");
        home <- jmx.systemProperties.get("server.home");
        name <- jmx.systemProperties.get("server.name")
      ) yield {
        JettySignature(release = version, home = home, domain = mb.domain)
      }
    }
  }

  private def lookup4webmethodsSignature(jmx: JMX) = {
    val wmIsBaseDirOption = (jmx.systemProperties.get("WM_HOME") map { _ + "/IntegrationServer" }) orElse {
      jmx.get("WmTomcat:type=Engine") map { engine =>
        engine[String]("baseDir").split("/").init.mkString("/")
      }
    }
    for (
      wmhome <- wmIsBaseDirOption
    ) yield {
      val isjars = jmx.systemProperties.get("wm.server.class.path") map { _.split(":").toList filter { _.contains("IS") } }
      val relsfromjar = isjars map { _.map(_.split("/").last.replace("IS_", "").replace(".jar", "").replace("-", ".")) }
      val rel = relsfromjar map { _.head }

      WebMethodsSignature(rel getOrElse "unknown", wmhome)
    }
  }

  private def lookup4jbossSignature(jmx: JMX) = {
    // At least ok with 7.1.x; TODO check with older releases
    for (
      jbossas <- jmx.get("jboss.as:management-root=server");
      release <- jbossas.getString("releaseVersion");
      core <- jmx.get("jboss.as:core-service=server-environment");
      baseDir <- core.getString("baseDir")
    ) yield {
      JBossSignature(
        release = release,
        home = baseDir
      )
    }
  }

  private def lookup4tomcatSignature(jmx: JMX) = {
    for (
      catalina <- jmx.get("Catalina:type=Server");
      engine <- jmx.get("Catalina:type=Engine");
      serverInfo <- catalina.getString("serverInfo");
      Array(id, rel) <- Option(serverInfo.split("/", 2))
    ) yield {
      lazy val basedirFromProps = jmx.systemProperties.get("catalina.home")
      val basedir = engine.getString("baseDir") getOrElse basedirFromProps.get
      TomcatSignature(rel, basedir)
    }
  }

  def whoami(jmx: JMX): Option[ASSignature] = {
    // The order is important, most specific to the most general.
    // (for example tomcat is sometimes embedded into other application servers)
    val searchOrder: Stream[Option[ASSignature]] = Stream(
      lookup4jbossSignature(jmx),
      lookup4jettySignature(jmx),
      lookup4jonasSignature(jmx),
      lookup4webmethodsSignature(jmx),
      lookup4tomcatSignature(jmx)
    )
    searchOrder.find(_.isDefined).map(_.get)
  }
}




