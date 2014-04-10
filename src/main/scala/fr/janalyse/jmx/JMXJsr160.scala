package fr.janalyse.jmx

import javax.management.ObjectName
import javax.management.MBeanInfo
import javax.management.MBeanAttributeInfo
import com.typesafe.scalalogging.slf4j.Logging

trait JMXJsr160 extends Logging {
  def getMBeanInfo(objectName: ObjectName):MBeanInfo
  
  def getAttributesMetaData(objectName: ObjectName):List[AttributeMetaData] = {
    try {
      val attrsInfo: List[MBeanAttributeInfo] = getMBeanInfo(objectName).getAttributes().toList
      val attrsMetaData = attrsInfo map { ai =>
        val name = ai.getName()
        val adesc = ai.getDescription()
        val atype = ai.getType()
        val rw = ai.isWritable()
        AttributeMetaData(name, adesc, atype, rw)
      }
      attrsMetaData
    } catch {
      case e: Exception =>
        new Logging() {
          //e.printStackTrace
          logger.warn("Couln't build attributes map for MBean %s (%s)".format(objectName.toString, e.getMessage))
        }
        List.empty
    }
   }

  /*
  def getAttributesMap(objectName:ObjectName): Map[String, RichAttribute] = {
    try {
      val attrsInfo: List[MBeanAttributeInfo] = getMBeanInfo(objectName).getAttributes().toList
      val richattrs = attrsInfo map { ai =>
        val n = ai.getName()
        //val d = if (ai.getDescription() == ai.getName || ai.getDescription().trim.size == 0) None else Some(ai.getDescription())
        val d = Option(ai.getDescription()).map(_.trim).filterNot(_.size == 0).filterNot(_ == n)
        val a: RichAttribute = ai.getType() match {
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
          case "javax.management.openmbean.CompositeData" | "javax.management.openmbean.CompositeDataSupport" => RichCompositeDataAttribute(n, d)
          //case "[Ljavax.management.openmbean.CompositeData;" =>
          //case "[Ljavax.management.ObjectName;" => 
          case x =>
            //println("Warning: Not supported jmx attribute value type %s for %s mbean %s".format(x, n, name))
            //println(x)
            RichGenericAttribute(ai.getName(), d)
        }
        n -> a
      }
      richattrs.toMap
      //def apply[A](attrname:String) = jmx.rmiConnection.getAttribute(objectName,attrname,null).asInstanceOf[A]
    } catch {
      case e: Exception =>
        new Logging() {
          //e.printStackTrace
          logger.warn("Couln't build attributes map for MBean %s (%s)".format(objectName.toString, e.getMessage))
        }
        Map.empty[String, RichAttribute]
    }
  }
  * 
  */
}