package fr.janalyse.jmx

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers
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
    def attrsMap(opts: JMXOptions) = {
      JMX.once(opts) { jmx =>
        for { mbean <- jmx.mbeans; attr <- mbean.attributes }
          yield new ObjectName(mbean.name) -> attr.name
      }.toSet
    }
    val attrsUsingJsr160 = attrsMap(jmxOpts)
    val attrsUsingJolokia = attrsMap(jolokiaOpts)

    val missing = attrsUsingJsr160 -- attrsUsingJolokia
    for { miss <- missing} {
      info(s"Missing : $miss")
    }
    info(s"Found ${attrsUsingJolokia.size} attributes using jolokia")
    info(s"Found ${attrsUsingJsr160.size} attributes using jsr160")
    attrsUsingJolokia.size should equal(attrsUsingJsr160.size)

  }

}