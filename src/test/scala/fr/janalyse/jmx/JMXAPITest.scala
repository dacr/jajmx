/*
 * Copyright 2017 David Crosson
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

import org.junit.Test
import junit.framework.TestCase
import org.junit.Assert._

import java.rmi.registry.LocateRegistry
import java.lang.management.ManagementFactory
import javax.management.remote.JMXConnectorServerFactory
import javax.management.remote.JMXConnectorFactory
import javax.management.remote.JMXServiceURL
//import scala.beans.BeanProperty
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


class JMXAPITest extends TestCase {

  def info(msg:String):Unit = {
    println(msg)
  }

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
    def getSlowName(): String
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
  case class Someone(name: String, /*@BeanProperty */age: Int) extends SomeoneMBean {
    def getAge():Int=age
    def getSlowName():String = {
      Thread.sleep(5*1000L)
      name
    }
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
  def testMBeanCreateAndUse:Unit =  {
    val marvin = Someone("Marvin", 30)
    try {
      JMX.register(marvin, "people:name="+marvin.name)

      val marvinAgeFromJMX = JMX.once() { jmx =>
        jmx("people:name="+marvin.name).get[Int]("Age")
      }
      assertEquals(marvinAgeFromJMX, Some(30))
      info("Marvin age is %s".format(marvinAgeFromJMX map { _.toString } getOrElse "Unknown"))
    } finally {
      JMX.unregister("people:name="+marvin.name)
    }
  }

  def testMBeanGetTimeout:Unit =  {
    val marvin = Someone("Marvin", 30)
    try {
      JMX.register(marvin, "people:name="+marvin.name)

      val (duration, marvinNameFromJMX) = howLongFor { () =>
        JMX.once() { jmx =>
          jmx.setTimeout(2000)
          jmx("people:name="+marvin.name).get[String]("SlowName")
        }
      }
      assertTrue(duration < 2000)
      assertEquals(marvinNameFromJMX, None)
    } finally {
      JMX.unregister("people:name="+marvin.name)
    }
  }

  def testMBeanCall:Unit =  {
    val marvin = Someone("Marvin", 30)
    try {
      JMX.register(marvin, "people:name="+marvin.name)

      JMX.once() { jmx =>
        val jmxmarvin = jmx("people:name="+marvin.name)
        assertEquals(jmxmarvin.call[String]("lowercase", "TOTO"), Some("toto"))
        assertEquals(jmxmarvin.call[Array[String]]("arraylowercase", Array("TOTO", "TATA")).map(_.toList) , Some(List("toto", "tata")))
        assertEquals(jmxmarvin.call[Array[Int]]("addInt", 1, Array(1, 2)).map(_.toList), Some(List(2, 3)))
        //jmxmarvin.call[Array[Short]]("addShort", 1.toShort, Array(1.toShort, 2.toShort)).map(_.toList) should equal(Some(List(2.toShort, 3.toShort)))
        assertEquals(jmxmarvin.call[Array[Long]]("addLong", 1L, Array(1L, 2L)).map(_.toList), Some(List(2L, 3L)))
        assertEquals(jmxmarvin.call[Array[Float]]("addFloat", 1f, Array(1f, 2f)).map(_.toList), Some(List(2f, 3f)))
        assertEquals(jmxmarvin.call[Array[Double]]("addDouble", 1d, Array(1d, 2d)).map(_.toList), Some(List(2d, 3d)))
        assertEquals(jmxmarvin.call[Array[Boolean]]("fillBoolean", true, Array(false)).map(_.toList), Some(List(true)))
        assertEquals(jmxmarvin.call[Array[Byte]]("fillByte", 255.toByte, Array(0.toByte)).map(_.toList), Some(List(255.toByte)))
        assertEquals(jmxmarvin.call[Array[Char]]("fillChar", 'X', Array('_')).map(_.toList), Some(List('X')))
      }
    } finally {
      JMX.unregister("people:name="+marvin.name)
    }
  }

  // ================================================================================================

  def testSimpleJMX:Unit = {
    JMX.once() { jmx =>
      val os = jmx("java.lang:type=OperatingSystem")
      val List(name, version) = os[String]("Name", "Version")

      assertTrue(name.size > 0)
      assertTrue(version.size > 0)
      info("OS Name & Version : %s %s".format(name, version))

      val runtime = jmx("java.lang:type=Runtime")
      val vmname: String = runtime("VmName")
      assertTrue(vmname.contains("Java"))
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
  def testShortAndSafeUsage:Unit =  {
    JMX.once() { jmx =>
      val osname1 = jmx.get("java.lang:type=OperatingSystem") flatMap { _.getString("Name") }
      //or
      val osname2 = for (os <- jmx.os; name <- os.getString("Name")) yield name
    }
  }
  // ================================================================================================
  def testOneLineJmxBrowsing:Unit =  {
    val results =
      for (mb <- JMX().mbeans; attr <- mb.attributes; value <- mb.getString(attr)) yield attr.name -> value

    assertTrue(results.size > 0)
  }
  // ================================================================================================
  def testJmxMbeansQuery:Unit =  {
    JMX.once() { jmx =>
      assertTrue(jmx.mbeans("java.lang:type=GarbageCollector,*").size > 0)
      assertTrue(jmx.mbeans("Catalina:type=ThreadPool,*").size == 0)
    }
  }
  // ================================================================================================
  def testGetNumericAttributes:Unit = {
    JMX.once() { jmx =>
      for (os <- jmx.os) {
        val numAttrs = os.attributes collect { case x: RichNumberAttribute => x }
        val numValues = numAttrs map { os.getDouble(_) }
      }
    }
  }
  // ================================================================================================
  def testJmxShorcuts:Unit = {
    JMX.once() { jmx =>
      assertNotEquals(jmx.os, None)
      assertNotEquals(jmx.runtime, None)
      assertNotEquals(jmx.memory, None)
      assertNotEquals(jmx.threading, None)
    }
  }
  // ================================================================================================
  def testJmxOpenDataConversions:Unit = {
    JMX.once() { jmx =>

      // ----- CompositeData support
      for (mem <- jmx.memory) {
        val heapusage = mem[CompositeDataSupport]("HeapMemoryUsage").toWrapper
        val used = heapusage.getNumber("used")
        info("current heap usage : %d Mo".format(used.get.longValue / 1024 / 1024))
        assertNotEquals(used, None)
      }

      // ----- CompositeData support
      for (mem <- jmx.memory) {
        val heapusage = mem.getNumberComposite("HeapMemoryUsage").get
        val used = heapusage.get("used")
        info("current heap usage : %d Mo".format(used.get.longValue / 1024 / 1024))
        assertNotEquals(used, None)
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
        assertNotEquals(jv, None)
        assertEquals(jv.get("value"), util.Properties.javaVersion)
        assertEquals(sysprops.get("java.version", "value"), Some(util.Properties.javaVersion))
        assertEquals(sysprops.getString("java.version", "value"), Some(util.Properties.javaVersion))
      }

      // ----- CompositeData array support
      val hotspotDiag = jmx.get("com.sun.management:type=HotSpotDiagnostic")
      val diagOpts = hotspotDiag.get[Array[CompositeData]]("DiagnosticOptions")
      val diagProps = (diagOpts map { cd => cd.get("name") -> cd.get("value") }).toMap

      assertTrue(diagProps.contains("PrintGC"))
    }
  }
  // ================================================================================================
  def testSimpleCompositeDataRetrieve:Unit =  {
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

  def testMoreComplexNestedCompositeDataValues:Unit =  {
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

        assertTrue(aftergc.isDefined)
        assertTrue(beforegc.isDefined)

      }
    }
  }

  // ================================================================================================

  def testThreadDumpsAndCPUTimesTest:Unit =  {
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
      
      assertTrue(dump1opt.isDefined)
      assertTrue(dump2opt.isDefined)
      
      val th1opt = dump1opt.flatMap(_.threads.find(_.name == th2testName))
      assertTrue(th1opt.isDefined)
      
      val th2opt = dump2opt.flatMap(_.threads.find(_.name == th2testName))
      assertTrue(th2opt.isDefined)
      
      assertTrue(th1opt.get.stack.map(_.methodName).contains("computeLoop"))
      assertTrue(th2opt.get.stack.map(_.methodName).contains("computeLoop"))
      
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

      assertTrue(cputimeInMSopt.isDefined)
      
      val cpuPercent = cputimeInMSopt.get*100/during
      info("Thread CPU usage = "+cpuPercent)
      
      // testME thread will of course use 1 cpu, so percent should be >90%

      assertTrue(cpuPercent > 50L)
    }
  }


  def ignored_testComplexTypes:Unit =  {
    JMX.once() { jmx =>
      val rt  = jmx("java.lang:type=Runtime")
      val th  = jmx("java.lang:type=Threading")
      val mem = jmx("java.lang:type=Memory")
            
      // Array[String]
      val args = rt.get[List[String]]("InputArguments")
      args.get.head
      
      // Array[Long]
      val ids = th.get[List[BigInt]]("AllThreadIds")
      ids.get.head
      
      // TabularDataSupport
      val props = rt.get[Map[String,String]]("SystemProperties")
      props.get.get("user.dir")
      
      // CompositeDataSupport
      val heap = mem.get[Map[String,BigInt]]("HeapMemoryUsage")
      heap.get.get("max")
    }
  }
  
}

