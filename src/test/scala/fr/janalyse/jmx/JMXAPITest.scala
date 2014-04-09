/*
 * Copyright 2012 David Crosson
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

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.junit.JUnitRunner
import java.rmi.registry.LocateRegistry
import java.lang.management.ManagementFactory
import javax.management.remote.JMXConnectorServerFactory
import javax.management.remote.JMXConnectorFactory
import javax.management.remote.JMXServiceURL
import scala.reflect.BeanProperty
import javax.management.openmbean.CompositeDataSupport
import javax.management.openmbean.TabularDataSupport
import javax.management.openmbean.CompositeData
import javax.management.RuntimeMBeanException

/*
trait JMXSelftInit {
  val jmxself = synchronized { try {
      JMX.connect("127.0.0.1",4500) { implicit jmx => 
      }
    } catch {
      case x =>
        LocateRegistry.createRegistry(4500)
        val url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://localhost:4500/jmxapitestrmi")
        val mbs = ManagementFactory.getPlatformMBeanServer()
        val cs = JMXConnectorServerFactory.newJMXConnectorServer(url, null, mbs)
        cs.start
        Thread.sleep(500) // because some computers are really too fast
    }
  }
}
*/

@RunWith(classOf[JUnitRunner])
class JMXAPITest extends FunSuite with ShouldMatchers {

  def howLongFor[T](what: () => T) = {
    val begin = System.currentTimeMillis()
    val result = what()
    val end = System.currentTimeMillis()
    (end - begin, result)
  }

  // ================================================================================================

  // -- Define Some MBean Interface 
  trait SomeoneMBean {
    def getAge(): Int
    def lowercase(input: String): String
    def arraylowercase(input: Array[String]): Array[String]
    def addInt(toadd: Int, addTo: Array[Int]): Array[Int]
    //def addShort(toadd: Short, addTo: Array[Short]): Array[Short]
    def addLong(toadd: Long, addTo: Array[Long]): Array[Long]
    def addFloat(toadd: Float, addTo: Array[Float]): Array[Float]
    def addDouble(toadd: Double, addTo: Array[Double]): Array[Double]
    def fillBoolean(fillWith: Boolean, that: Array[Boolean]): Array[Boolean]
    def fillByte(fillWith: Byte, that: Array[Byte]): Array[Byte]
    def fillChar(fillWith: Char, that: Array[Char]): Array[Char]
  }
  // -- Our JMX Managed class 
  case class Someone(name: String, @BeanProperty age: Int) extends SomeoneMBean {
    def lowercase(input: String) = input.toLowerCase()
    def arraylowercase(input: Array[String]) = input.map(_.toLowerCase())
    def addInt(toadd: Int, addTo: Array[Int]) = addTo.map(_ + toadd)
    //def addShort(toadd: Short, addTo: Array[Short]) = addTo.map(_ + toadd)
    def addLong(toadd: Long, addTo: Array[Long]) = addTo.map(_ + toadd)
    def addFloat(toadd: Float, addTo: Array[Float]) = addTo.map(_ + toadd)
    def addDouble(toadd: Double, addTo: Array[Double]) = addTo.map(_ + toadd)
    def fillBoolean(fillWith: Boolean, that: Array[Boolean]) = that.map(x => fillWith)
    def fillByte(fillWith: Byte, that: Array[Byte]) = that.map(x => fillWith)
    def fillChar(fillWith: Char, that: Array[Char]) = that.map(x => fillWith)
  }

  // -- The test case : Creates a JMX managed instance, and queries it
  test("MBean create & use") {
    val marvin = Someone("Marvin", 30)
    try {
      JMX.register(marvin, s"people:name=${marvin.name}")

      val marvinAgeFromJMX = JMX.once() { jmx =>
        jmx("people:name=Marvin").get[Int]("Age")
      }
      marvinAgeFromJMX should equal(Some(30))
      info("Marvin age is %s".format(marvinAgeFromJMX map { _.toString } getOrElse "Unknown"))
    } finally {
      JMX.unregister(s"people:name=${marvin.name}")
    }
  }

  test("MBean call tests") {
    val marvin = Someone("Marvin", 30)
    try {
      JMX.register(marvin, s"people:name=${marvin.name}")

      JMX.once() { jmx =>
        val jmxmarvin = jmx(s"people:name=${marvin.name}")
        jmxmarvin.call[String]("lowercase", "TOTO") should equal(Some("toto"))
        jmxmarvin.call[Array[String]]("arraylowercase", Array("TOTO", "TATA")).map(_.toList) should equal(Some(List("toto", "tata")))
        jmxmarvin.call[Array[Int]]("addInt", 1, Array(1, 2)).map(_.toList) should equal(Some(List(2, 3)))
        //jmxmarvin.call[Array[Short]]("addShort", 1.toShort, Array(1.toShort, 2.toShort)).map(_.toList) should equal(Some(List(2.toShort, 3.toShort)))
        jmxmarvin.call[Array[Long]]("addLong", 1L, Array(1L, 2L)).map(_.toList) should equal(Some(List(2L, 3L)))
        jmxmarvin.call[Array[Float]]("addFloat", 1f, Array(1f, 2f)).map(_.toList) should equal(Some(List(2f, 3f)))
        jmxmarvin.call[Array[Double]]("addDouble", 1d, Array(1d, 2d)).map(_.toList) should equal(Some(List(2d, 3d)))
        jmxmarvin.call[Array[Boolean]]("fillBoolean", true, Array(false)).map(_.toList) should equal(Some(List(true)))
        jmxmarvin.call[Array[Byte]]("fillByte", 255.toByte, Array(0.toByte)).map(_.toList) should equal(Some(List(255.toByte)))
        jmxmarvin.call[Array[Char]]("fillChar", 'X', Array('_')).map(_.toList) should equal(Some(List('X')))
      }
    } finally {
      JMX.unregister(s"people:name=${marvin.name}")
    }
  }

  // ================================================================================================

  test("Simple JMX test") {
    JMX.once() { jmx =>
      val os = jmx("java.lang:type=OperatingSystem")
      val List(name, version) = os[String]("Name", "Version")

      name.size should be > (0)
      version.size should be > (0)
      info("OS Name & Version : %s %s".format(name, version))

      val runtime = jmx("java.lang:type=Runtime")
      val vmname: String = runtime("VmName")
      vmname should include("Java")
      info("JVM NAME : %s".format(vmname))

      val threading = jmx("java.lang:type=Threading")
      threading.set("ThreadCpuTimeEnabled", true)
      val cpuTimeEnabled = threading.get[Boolean]("ThreadCpuTimeEnabled")
      info("isThreadCpuTimeEnabled=".format(cpuTimeEnabled))

      val mem = jmx("java.lang:type=Memory")
      mem.call("gc")
      info("Explicit GC invoked")
    }
  }

  // ================================================================================================
  test("short and safe usage") {
    JMX.once() { jmx =>
      val osname1 = jmx.get("java.lang:type=OperatingSystem") flatMap { _.getString("Name") }
      //or
      val osname2 = for (os <- jmx.os; name <- os.getString("Name")) yield name
    }
  }
  // ================================================================================================
  test("one line jmx browsing") {
    val results =
      for (mb <- JMX().mbeans; attr <- mb.attributes; value <- mb.getString(attr))
        yield attr.name -> value

    results.size should be > (0)
  }
  // ================================================================================================
  test("jmx mbeans query") {
    JMX.once() { jmx =>
      jmx.mbeans("java.lang:type=GarbageCollector,*").size should be > (0)
      jmx.mbeans("Catalina:type=ThreadPool,*").size should equal(0)
    }
  }
  // ================================================================================================
  test("get numeric attributes") {
    JMX.once() { jmx =>
      for (os <- jmx.os) {
        val numAttrs = os.attributes collect { case x: RichNumberAttribute => x }
        val numValues = numAttrs map { os.getDouble(_) }
      }
    }
  }
  // ================================================================================================
  test("jmx shorcuts") {
    JMX.once() { jmx =>
      jmx.os should not equal (None)
      jmx.runtime should not equal (None)
      jmx.memory should not equal (None)
      jmx.threading should not equal (None)
    }
  }
  // ================================================================================================
  test("jmx open data conversions") {
    JMX.once() { jmx =>

      // ----- CompositeData support
      for (mem <- jmx.memory) {
        val heapusage = mem[CompositeDataSupport]("HeapMemoryUsage").toWrapper
        val used = heapusage.getNumber("used")
        info("current heap usage : %d Mo".format(used.get.longValue / 1024 / 1024))
        used should not equal (None)
      }

      // ----- CompositeData support
      for (mem <- jmx.memory) {
        val heapusage = mem.getNumberComposite("HeapMemoryUsage").get
        val used = heapusage.get("used")
        info("current heap usage : %d Mo".format(used.get.longValue / 1024 / 1024))
        used should not equal (None)
      }

      // ----- Browsing system properties
      for (
        rt <- jmx.runtime;
        props <- rt.get[TabularDataSupport]("SystemProperties");
        (key, values) <- props.content;
        value <- values.get("value")
      ) {
        info("%s = %s".format(key, value.toString))
      }

      // ----- System Properties and TabularData support
      for (rt <- jmx.runtime) {
        val sysprops = rt[TabularDataSupport]("SystemProperties").toWrapper
        val jv = sysprops.get("java.version")
        jv should not equal (None)
        jv.get("value") should equal(util.Properties.javaVersion)
        sysprops.get("java.version", "value") should equal(Some(util.Properties.javaVersion))
        sysprops.getString("java.version", "value") should equal(Some(util.Properties.javaVersion))
      }

      // ----- CompositeData array support
      val hotspotDiag = jmx.get("com.sun.management:type=HotSpotDiagnostic")
      val diagOpts = hotspotDiag.get[Array[CompositeData]]("DiagnosticOptions")
      val diagProps = (diagOpts map { cd => cd.get("name") -> cd.get("value") }).toMap

      diagProps should contain key ("PrintGC")
    }
  }
  // ================================================================================================
  test("Simple composite data retrieve") {
    JMX.once() { jmx =>
      for (
        mem <- jmx.memory;
        data <- mem.getNumberComposite("HeapMemoryUsage");
        (name, value) <- data
      ) {
        info("%s = %d".format(name, value.longValue))
      }
    }
  }
  // ================================================================================================

  test("More complex nested composite data values") {
    import java.lang.management.MemoryUsage
    JMX.once() { jmx =>
      for (
        scavenge <- jmx.get("java.lang:type=GarbageCollector,name=PS MarkSweep");
        lastgc <- scavenge.get[CompositeData]("LastGcInfo").map(_.toWrapper)
      ) {
        val id = lastgc.get[Int]("id")
        val startTime = lastgc.get[Long]("startTime")
        val endTime = lastgc.get[Long]("endTime")
        val duration = lastgc.get[Long]("duration")
        val aftergc = lastgc.get[Map[String, MemoryUsage]]("memoryUsageAfterGc")
        val beforegc = lastgc.get[Map[String, MemoryUsage]]("memoryUsageBeforeGc")

        aftergc.isDefined should equal(true)
        beforegc.isDefined should equal(true)

      }
    }
  }

  // ================================================================================================

  test("Thread dumps & CPU times test") {
    JMX.once() { jmx =>
      val th2testName = "testME"
      val during = 5 * 1000L
      // -----------
      val th2test = new Thread(th2testName) {
        def computeToto(x:Long) = x+1
        def computeLoop() {
          val time = System.currentTimeMillis()
          var count: Long = 0
          while (System.currentTimeMillis() - time <= during) {
            count = computeToto(count + 123) % 100000L
          }          
          Thread.sleep(1*1000L)
        }
        override def run() {
          computeLoop()
        }
      }
      th2test.start
      // -----------
      val dump1opt = jmx.threadsDump(5, true)
      Thread.sleep(during)
      val dump2opt = jmx.threadsDump(5, true)
      
      dump1opt should be ('defined)
      dump2opt should be ('defined)
      
      val th1opt = dump1opt.flatMap(_.threads.find(_.name == th2testName))
      th1opt should be ('defined)
      
      val th2opt = dump2opt.flatMap(_.threads.find(_.name == th2testName))
      th2opt should be ('defined)
      
      th1opt.get.stack.map(_.methodName) should contain("computeLoop")
      th2opt.get.stack.map(_.methodName) should contain("computeLoop")
      
      val cputimeInMSopt = for {
        dump1 <- dump1opt
        dump2 <- dump2opt
        th1   <- th1opt
        th2   <- th2opt
        cputimes1opt <- dump1.cpuTimes.get(th1.id)
        cputimes2opt <- dump2.cpuTimes.get(th2.id)
        cputimes1 <- cputimes1opt
        cputimes2 <- cputimes2opt
      } yield {
        (cputimes2 - cputimes1)/1000/1000 // Now in milliseconds instead of nanoseconds
      }

      cputimeInMSopt should be ('defined)
      
      val cpuPercent = cputimeInMSopt.get*100/during
      info(s"Thread CPU usage = ${cpuPercent}")
      
      // testME thread will of course use 1 cpu, so percent should be >90%
      cpuPercent should be >(90L)
    }
  }

}

