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

import java.lang.management.ManagementFactory
import javax.management.ObjectName
import javax.management.remote.rmi.RMIConnection
import javax.management.remote.rmi.RMIServer
import javax.management.remote.rmi.RMIServerImpl_Stub
import javax.management.openmbean.{ CompositeData, CompositeDataSupport }
import javax.management.openmbean.{ TabularData, TabularDataSupport }
import java.rmi.MarshalledObject
import java.rmi.UnmarshalException
import javax.management.openmbean.CompositeData
import scala.collection.JavaConversions._
import javax.management.remote.JMXConnector
import javax.management.remote.JMXServiceURL
import javax.management.remote.{ JMXConnectorServerFactory, JMXConnectorFactory }
import java.rmi.server.RMIClientSocketFactory
import javax.management.remote.rmi.RMIJRMPServerImpl
import java.rmi.server.RMIServerSocketFactory
import javax.net.ServerSocketFactory
import java.net.ServerSocket
import java.rmi.server.RMISocketFactory
import javax.management.RuntimeMBeanException
import scala.math.ScalaNumber
import javax.management.MBeanInfo
import javax.management.MBeanServerConnection
import javax.management.remote.JMXConnectorServer
import java.rmi.registry.LocateRegistry

import com.typesafe.scalalogging.slf4j.Logging

trait JMX {
  val options: Option[JMXOptions]
  // Service name introduced in order to mixup threads dumps, and allow data processing (eg groupBy)
  val serviceName = options.flatMap(_.name) getOrElse "default"

  def close()
  def exists(name: String): Boolean
  def domains: List[String]
  def mbeans(query: String): List[RichMBean]
  def mbeans(): List[RichMBean]
  def apply(name: String): RichMBean
  def get(name: String): Option[RichMBean] = if (exists(name)) Some(apply(name)) else None

  def whoami(): Option[ASSignature] = ASSignature.whoami(this)

  def os() = get("java.lang:type=OperatingSystem")
  def runtime() = get("java.lang:type=Runtime")
  def threading() = get("java.lang:type=Threading")
  def memory() = get("java.lang:type=Memory")
  def classLoading() = get("java.lang:type=ClassLoading")
  def compilation() = get("java.lang:type=Compilation")

  def gcforce() { memory.map(_.call("gc")) }

  def verbosegc(state:Boolean=true) { memory.map(_.set("Verbose", state)) }
  
  def threadCpuTime(id:Long) = {
    for {
      th <- threading
      cpuTime <- getThreadCpuTime(id, th)
    } yield cpuTime
  }
  
  def uptimeInMs = for {
    rt <- runtime
    uptime <- rt.get[Long]("Uptime")
  } yield uptime
  
  
  
  def threadsDump(stackMaxDepth: Int = 9999, withCpuTime:Boolean=false): Option[ServiceThreads] = {
    javaSpecificationVersion match {
      case Some("1.5") => incrementalThreadDump(stackMaxDepth, withCpuTime)
      case _ => simultaneousThreadDump(stackMaxDepth, withCpuTime)
    }
  }

  private def getThreadCpuTime(id:Long, threading:RichMBean) = 
    threading.call[Long]("getThreadCpuTime", id).filter(_>=0)

  private def incrementalThreadDump(stackMaxDepth: Int, withCpuTime:Boolean): Option[ServiceThreads] = { // Java 5
    for {
      th <- threading
      ids <- th.get[Array[Long]]("AllThreadIds")
      infos <- th.call[Array[CompositeData]]("getThreadInfo", ids, stackMaxDepth)
    } yield {
      val threads =
        infos
          .toList
          .filterNot(_ == null)
          .filter(_.get("threadId") != null)
          .map(cd => ThreadInfo(serviceName, cd))
          

      val cpuTimes:Map[Long,Option[Long]] = 
        if (withCpuTime) threads.map(_.id).map(id => id->getThreadCpuTime(id,th)).toMap 
        else Map.empty
      ServiceThreads(serviceName, threads, cpuTimes)
    }
  }

  private def simultaneousThreadDump(stackMaxDepth: Int, withCpuTime:Boolean): Option[ServiceThreads] = { // Starting from Java 6
    for {
      th <- threading
      infos <- th.call[Array[CompositeData]]("dumpAllThreads", true, true)
    } yield {
      val threads = infos.map(ThreadInfo(serviceName, _)).toList
      /* TODO : Operation not always available, understand why...
      val cpuTimes:Map[Long,Option[Long]] = if (withCpuTime) {
        val ids = threads.map(_.id).toArray
        th.call[Array[Long]]("getThreadCpuTime", ids)
          .map{ ct => ids.zip(ct).map{case (id,thcpu)=> id->Option(thcpu)}.toMap}
          .getOrElse(Map.empty)
      } else Map.empty
      */
      val cpuTimes:Map[Long,Option[Long]] = 
        if (withCpuTime) threads.map(_.id).map(id => id->getThreadCpuTime(id,th)).toMap
        else Map.empty
      ServiceThreads(serviceName, threads, cpuTimes)
    }
  }

  lazy val javaHome:  Option[String] = systemProperties.get("java.home")
  lazy val userHome:  Option[String] = systemProperties.get("user.home")
  lazy val userName:  Option[String] = systemProperties.get("user.name")
  lazy val osName:    Option[String] = systemProperties.get("os.name")
  lazy val osVersion: Option[String] = systemProperties.get("os.version")
  lazy val javaVersion : Option[String] = systemProperties.get("java.version")
  lazy val javaRuntimeVersion : Option[String] = systemProperties.get("java.runtime.version")
  lazy val javaSpecificationVersion: Option[String] = systemProperties.get("java.specification.version") // 1.5, 1.6, ...

  lazy val systemProperties = {
    var sysprops = Map.empty[String, String]
    for {
      rt <- runtime;
      props <- rt.get[TabularDataSupport]("SystemProperties");
      (key, values) <- props.content;
      value <- values.get("value")
    } { sysprops += key -> value.toString }
    sysprops
  }
  protected def buildOperationSignature(args: Array[Any]) = {
    /* */
    // http://home.pacifier.com/~mmead/jni/cs510ajp/index.html  /   http://en.wikipedia.org/wiki/Java_Native_Interface#Mapping_types
    args map {
      case _: Int => "int"
      case _: Short => "short"
      case _: Long => "long"
      case _: Float => "float"
      case _: Double => "double"
      case _: Boolean => "boolean"
      case _: Byte => "byte"
      case _: Char => "char"
      case _: String => "java.lang.String"
      case _: Array[Int] => "[I"
      case _: Array[Short] => "[S"
      case _: Array[Long] => "[J"
      case _: Array[Float] => "[F"
      case _: Array[Double] => "[D"
      case _: Array[Boolean] => "[Z"
      case _: Array[Byte] => "[B"
      case _: Array[Char] => "[C"
      case _: Array[String] => "[Ljava.lang.String;"
    }
  }

  def getAttributesMetaData(objectName: ObjectName):List[AttributeMetaData]
  
  def getAttributesMap(objectName: ObjectName): Map[String, RichAttribute] = {
    val result = for {
      meta <- getAttributesMetaData(objectName)
    } yield {
      val n = meta.name
      val d = Option(meta.adesc).map(_.trim).filterNot(_.size == 0).filterNot(_ == n)
      val a: RichAttribute = meta.atype match {
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
        case "javax.management.openmbean.CompositeData"
            |"javax.management.openmbean.CompositeDataSupport" => RichCompositeDataAttribute(n, d)
        //case "[Ljavax.management.openmbean.CompositeData;" =>
        //case "[Ljavax.management.ObjectName;" => 
        case x =>
          //println("Warning: Not supported jmx attribute value type %s for %s mbean %s".format(x, n, name))
          //println(x)
          RichGenericAttribute(n, d)
      }
      n -> a
    }
    result.toMap
  }
  
}

object JMX extends Logging {
  def register(ob: Object, obname: ObjectName) = ManagementFactory.getPlatformMBeanServer.registerMBean(ob, obname)
  def unregister(obname: ObjectName) = ManagementFactory.getPlatformMBeanServer.unregisterMBean(obname)

  def usingJMX[T <: { def close() }, R](resource: T)(block: T => R) = {
    import language.reflectiveCalls
    try block(resource)
    finally resource.close
  }

  def once[R](
    host: String,
    port: Int,
    username: Option[String] = None,
    password: Option[String] = None)(block: JMX => R): R = {
    once[R](JMXOptions(host = host, port = port, username = username, password = password))(block)
  }

  def once[R]()(block: JMX => R): R = once[R](None)(block)
  def once[R](options: JMXOptions)(block: JMX => R): R = once[R](Some(options))(block)
  def once[R](someOptions: Option[JMXOptions])(block: JMX => R): R = usingJMX(getImpl(someOptions)) { block(_) }

  def apply(options: JMXOptions) = getImpl(Some(options))
  def apply(options: Option[JMXOptions]) = getImpl(options)
  //def apply(host: String, port: Int) = getImpl(Some(JMXOptions(host = host, port = port)))
  def apply(host: String, port: Int, username: Option[String] = None, password: Option[String] = None) = getImpl(Some(JMXOptions(host = host, port = port, username = username, password = password)))
  def apply() = getImpl()

  // For jboss "jboss-client.jar" is mandatory
  def jbossServiceURL(host: String, port: Int = 9999) = "service:jmx:remoting-jmx://%s:%d".format(host, port)
  def jonasServiceURL(host: String, port: Int = 1099, name: String = "jonas") = "service:jmx:rmi:///jndi/rmi://%s:%d/jrmpconnector_%s".format(host, port, name)
  def jsr160ServiceURL(host: String, port: Int = 2500) = "service:jmx:rmi:///jndi/rmi://%s:%d/jmxrmi".format(host, port)

  private def getImpl(options: Option[JMXOptions] = None): JMX = {
    options match {
      case None => getLocalImpl() // For quick test or evaluation purposes
      case Some(cfg) =>
        jolokiaLookup(cfg) orElse jmxLookup(cfg) getOrElse {
          throw new RuntimeException("Couldn't find any jmx connector for %s".format(cfg.toString))
        }
    }
  }

  private def jolokiaLookup(opt: JMXOptions) : Option[JMX] = {
    import org.apache.http.impl.client._
    import org.apache.http.client.methods.HttpGet
    import org.apache.http.auth._
    import org.apache.http.util.EntityUtils

	import opt.{host,port}
    
    def getHttpClient() = {
	    val credsProvider = new BasicCredentialsProvider()
	    for { username <- opt.username ;  password <- opt.password } {
	      credsProvider.setCredentials(
	        new AuthScope(host, port),
	        new UsernamePasswordCredentials(username, password))
	    }
	    HttpClients.custom()
	               .setDefaultCredentialsProvider(credsProvider)
	               .build()
    }

    val httpclient = getHttpClient()
    try {
      val context = opt.contextbase.getOrElse("/jolokia")
      val baseUrl = s"http://$host:$port" + context 
      val testUrl = baseUrl + "/version"
      val httpget = new HttpGet(testUrl)
      val response = httpclient.execute(httpget)
      try {
          val rc = response.getStatusLine().getStatusCode()
	      val entity = response.getEntity
	      val content = io.Source.fromInputStream(entity.getContent).getLines().mkString("\n")
	      EntityUtils.consume(entity)
	      if (content.contains("jolokia")) 
	        Some(new JMXjolokiaImpl(getHttpClient, baseUrl, Some(opt.copy(contextbase=Some(context)))))
	      else None
      } finally {
        response.close()
      }
    } catch {
      case e:java.net.SocketException => None // Nothing behind the specified port
      case e:org.apache.http.NoHttpResponseException => None // Not Http response !
    } finally {
        httpclient.close()
    }
  }
  
  private def jmxLookup(opt: JMXOptions): Option[JMX] = {
    getMBeanServerFromKnownJMXServiceUrl(opt) orElse {
      JMX.findMBeanServers(opt.host, opt.port, opt.credentials)
        .headOption
        .map(new JMXrmiImpl(_, Some(opt)))
    }
  }

  private def getMBeanServerFromKnownJMXServiceUrl(opt: JMXOptions): Option[JMX] = {
    opt.url map { u => new JMXclassicalImpl(jmxurl2connector(u, opt.credentials), Some(opt)) } orElse {
      val urls = Stream(
        jsr160ServiceURL(opt.host, opt.port),
        jonasServiceURL(opt.host, opt.port, opt.name.getOrElse("jonas")),
        jbossServiceURL(opt.host, opt.port)
      ).map(new JMXServiceURL(_))
      urls.find(jmxurlAlive(_, opt.credentials))
        .map(jmxurl2connector(_, opt.credentials))
        .map(new JMXclassicalImpl(_, Some(opt)))
    }
  }

  def jmxurl2connector(url: JMXServiceURL, credentialsopt: Option[Credentials] = None): JMXConnector = {
    //println(credentialsopt)
    credentialsopt match {
      case None => JMXConnectorFactory.connect(url)
      case Some(credentials) =>
        val env = new java.util.HashMap[String, Any]()
        env.put(JMXConnector.CREDENTIALS, Array[String](credentials.username, credentials.password))
        JMXConnectorFactory.connect(url, env)
    }
  }

  def jmxurlAlive(url: JMXServiceURL, credentialsopt: Option[Credentials]): Boolean = {
    val result = try {
      val connector = jmxurl2connector(url, credentialsopt)
      var jmx: Option[JMX] = None
      try {
        jmx = Some(new JMXclassicalImpl(connector))
        jmx.map(_.domains.size >= 0) getOrElse false
      } catch {
        case _: Exception => false
      } finally {
        jmx.foreach(_.close)
      }
    } catch {
      case _: Exception => false
    }
    //println(url.toString + " ==> " + result)

    result
  }

  private def getLocalImpl(): JMX = {
    val (jmxurl, chosenPort, additionnalCleaning) = JMX.jmxLocalInit()
    logger.warn("WARNING : No JMX options given, automatic self connect done (%s)".format(jmxurl.toString))
    new JMXclassicalImpl(jmxurl2connector(jmxurl), additionalCleaning = Some(additionnalCleaning))
  }

  def checkServiceURL(urlstr: String): String = {
    val url = new JMXServiceURL(urlstr)
    var jmxc: Option[JMXConnector] = None
    try {
      jmxc = Some(JMXConnectorFactory.connect(url))
      val mbsc = jmxc.get.getMBeanServerConnection()
      val domains = mbsc.getDomains()
    } finally { jmxc.foreach(_.close) }
    urlstr
  }

  // Self init procedure, of course more for tests or eval purposes 
  private def jmxLocalInit(): Tuple3[JMXServiceURL, Int, ()=>Any ] = {
    // TODO : Not safe...
    val csf = RMISocketFactory.getDefaultSocketFactory()
    var chosenPort: Option[Int] = None
    val ssf = new RMIServerSocketFactory {
      def createServerSocket(port: Int): ServerSocket = {
        val ssock = ServerSocketFactory.getDefault().createServerSocket(port)
        chosenPort = Some(ssock.getLocalPort())
        ssock
      }
    }
    LocateRegistry.createRegistry(0, csf, ssf)
    val url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://localhost:%d/jmxapitestrmi".format(chosenPort.get))
    val mbs = ManagementFactory.getPlatformMBeanServer()
    val cs = JMXConnectorServerFactory.newJMXConnectorServer(url, null, mbs)
    cs.start()
    //Thread.sleep(500) // TODO : To remove, how to know when everything is ready ?
    while(!cs.isActive()) Thread.sleep(100)
    (url, chosenPort.get, cs.stop )
  }

  def findMBeanServers(host: String, port: Int, credentials: Option[Credentials]): List[RMIConnection] = {
    def searchMissingClass(e: Throwable): String = {
      e match {
        case null => "?"
        case ex: ClassNotFoundException => ex.getMessage().trim().split("\\s+")(0)
        case _ => searchMissingClass(e.getCause)
      }
    }
    try {
      val lr = java.rmi.registry.LocateRegistry.getRegistry(host, port)
      //filterNot => some jonas MBeans causes registry data corruption if lookuped then all others call are in exception
      
      val entries = lr.list.toList
        .filterNot(_ contains "TMFactory")
        .filterNot(_ contains "RMI_SERVER_RPC")
        .filterNot(_ contains "joramActivationSpec")
      
      val results = entries map { entry =>
        try {
          val credentialsArray: Array[String] = credentials match {
            case None => Array()
            case Some(Credentials(username, password)) => Array(username, password)
          }
          lr.lookup(entry) match {
            case stub: RMIServerImpl_Stub => Some(stub.newClient(credentialsArray))
            case rsi: RMIServer => Some(rsi.newClient(credentialsArray))
            case r @ _ => logger.error("Do not know how to manage %s class %s".format(entry, r.getClass)); None
          }
        } catch {
          case e: UnmarshalException => 
            //println("%s - Missing = %s".format(entry, searchMissingClass(e)))
            //if (entry contains "jrmp") e.printStackTrace()
            None
        }
      }
      results.flatten
    }
  }
}
