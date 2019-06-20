package fr.janalyse.jmx

/*
import org.scalatest.FunSuite
import org.scalatest.Matchers._
import org.scalatest.junit.JUnitRunner
import javax.management.ObjectName


@RunWith(classOf[JUnitRunner])
class JMXJolokiaTest extends FunSuite with ShouldMatchers {

  /*
   * Those tests requires a local tomcat on 8080, running jolokia war
   * and a standard JSR 160 port configured on port 4500 
   * (to compare results between jolokia jmx and JSR160 jmx)
   */

  val jolokiaOpts = JMXOptions(host = "localhost", port = 8080)
  val jmxOpts = JMXOptions(host = "localhost", port = 4500)

  test("Simple JMX test") {
    JMX.once(jolokiaOpts) { jmx =>
      val os = jmx("java.lang:type=OperatingSystem")
      val List(name, version) = os[String]("Name", "Version")
      name.size should be > (0)
      version.size should be > (0)
      info("OS Name & Version : %s %s".format(name, version))
    }
  }

  test("Browsing & compare") {
    def attrsSet(opts: JMXOptions) = {
      JMX.once(opts) { jmx =>
        for { mbean <- jmx.mbeans; attr <- mbean.attributes }
          yield new ObjectName(mbean.name) -> attr.name
      }.toSet
    }
    val attrsUsingJsr160 = attrsSet(jmxOpts)
    val attrsUsingJolokia = attrsSet(jolokiaOpts)

    val missing = attrsUsingJsr160 -- attrsUsingJolokia
    for { miss <- missing} {
      info(s"Missing : $miss")
    }
    info(s"Found ${attrsUsingJolokia.size} attributes using jolokia")
    info(s"Found ${attrsUsingJsr160.size} attributes using jsr160")
    attrsUsingJolokia.size should equal(attrsUsingJsr160.size)
  }

  
  
  test("Numeric attributes") {
    JMX.once(jolokiaOpts) { jmx =>
        for (os <- jmx.os) {
          val numAttrs = os.attributes collect { case x: RichNumberAttribute => x }
          val numValues = numAttrs map { a => a.name -> os.getDouble(a).get }
          for{(k,v) <- numValues} info(s"$k=$v")
          numValues.size should be >(0)
        }
      }
  }
  
  test("Complex types") {
    JMX.once(jolokiaOpts) { jmx =>
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
*/
