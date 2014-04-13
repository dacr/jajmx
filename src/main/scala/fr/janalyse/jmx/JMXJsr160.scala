package fr.janalyse.jmx

import javax.management.ObjectName
import javax.management.MBeanInfo
import javax.management.MBeanAttributeInfo
import com.typesafe.scalalogging.slf4j.Logging
import scala.collection.JavaConversions._


trait JMXJsr160 extends JMX with Logging {
  
  def convert(in: Any):Object = {
    in match {
      case e:Int => BigInt(e)
      case e:Integer => BigInt(e)
      case e:Short => BigInt(e)
      case e:Long=> BigInt(e)
      case e:Double => new java.lang.Double(e)
      case e:Float => new java.lang.Double(e)
      case e:Boolean => new java.lang.Boolean(e)
      case e:Array[Object] => e.toList.map(convert)
      
      case e:javax.management.openmbean.CompositeDataSupport =>
        val res = for { (key,value) <- e.content} yield key->convert(value)
        res
        
      //case e:javax.management.openmbean.TabularDataSupport => 
      //  for { (key,data) <- e.content }
       
      case e => e
    }
  }
  
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
        logger.warn("Couln't build attributes map for MBean %s (%s)".format(objectName.toString, e.getMessage), e)
        List.empty
    }
   }
  
}