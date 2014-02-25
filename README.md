#JAJMX : High level scala JMX API

The goal is to simplify jmx operations on remote (or local) JVM. Work on this library is still in progress... but implemented features work well. One of the main usages of this jmx abstraction layer is to simplify extraction of jmx metrics such as getting jdbc connections or busy threads usage trends. This library only requires one IP and one PORT in order to connect to a remote JMX plateform, no service url is required !

[*JAnalyse software maven repository*](http://www.janalyse.fr/repository/)

[*Scala docs*](http://www.janalyse.fr/scaladocs/janalyse-jmx) 

*Current release* : 0.6.3 (for scala 2.10) 0.5.0 (for older scala releases) 

*Declare dependency in SBT as follow* :
```libraryDependencies += "fr.janalyse"   %% "janalyse-jmx" % "0.6.3" % "compile"``

*Add JAnalyse repository in SBT as follow* :
```resolvers += "JAnalyse Repository" at "http://www.janalyse.fr/repository/"```

##Console mode usage example, connecting to myself :

```
 $ java -jar jajmx.jar 
Welcome to Scala version 2.10.1 (Java HotSpot(TM) 64-Bit Server VM, Java 1.6.0_45).
Type in expressions to have them evaluated.
Type :help for more information.

scala> import jajmx._
import jajmx._

scala> val jmx = JMX()

scala> import jmx._
import jmx._

scala> verbosegc()

scala> gcforce()
[GC 62584K->30221K(310080K), 0.0015270 secs]
[Full GC 30221K->30014K(310080K), 0.1879440 secs]

scala> osVersion
res3: Option[String] = Some(3.7.10-gentoo)

scala> osName
res4: Option[String] = Some(Linux)

scala> mbeans.take(10).map(_.name).foreach{println _}
java.lang:type=Memory
java.lang:type=MemoryPool,name=PS Eden Space
java.lang:type=MemoryPool,name=PS Survivor Space
java.lang:type=MemoryPool,name=Code Cache
java.lang:type=GarbageCollector,name=PS MarkSweep
java.lang:type=Runtime
java.lang:type=ClassLoading
java.lang:type=Threading
java.util.logging:type=Logging
java.lang:type=Compilation

scala> val mythreading=get("java.lang:type=Runtime")
mythreading: Option[fr.janalyse.jmx.RichMBean] = Some(RichMBean(java.lang:type=Runtime,<function0>,<function1>,<function2>,<function2>))

scala> mythreading.map{_.attributes.foreach{attr => println(attr.name)}}
SynchronizerUsageSupported
ThreadCount
TotalStartedThreadCount
ThreadAllocatedMemorySupported
ThreadContentionMonitoringSupported
ThreadAllocatedMemoryEnabled
AllThreadIds
DaemonThreadCount
ThreadContentionMonitoringEnabled
CurrentThreadUserTime
PeakThreadCount
ObjectMonitorUsageSupported
ThreadCpuTimeSupported
CurrentThreadCpuTime
ThreadCpuTimeEnabled
CurrentThreadCpuTimeSupported

scala> close

scala> :q

```

##gcforce script

Only provide to the script, host and port of a remote JVM with JMX enabled, and this script will force the JVM to Garbage Collect major operation.

```scala
#!/bin/sh
exec java -verbosegc -jar jajmx.jar "$0" "$@"
!#

if (args.size < 2) {
  println("Usage   : jmxgrep host port")
  println("  args are not compliant so now let's connecting to myself, and force a gc...") 
}

import fr.janalyse.jmx._

val options = args.toList match {
  case host::port::_ => Some(JMXOptions(host,port.toInt))
  case _ => None
}
JMX.once(options) { _.gcforce }
```



A short gc force script (a self test) : 

```scala
#!/bin/sh
exec java -jar jajmx.jar "$0" "$@"
!#

jajmx.JMX.once() { jmx =>
  jmx.verbosegc() 
  jmx.gcforce()
}

```


##lsnum script

This scala script search for jmx numerical values. This jmxgrep script can be tested against itself : "lsnum"

```scala
#!/bin/sh
exec java -jar jajmx.jar "$0" "$@"
!#

import fr.janalyse.jmx._

val options = args.toList match {
  case host::port::_ => Some(JMXOptions(host,port.toInt))
  case _ => None
}

JMX.once(options) { jmx =>
  for {
    mbean <- jmx.mbeans
    attr  <- mbean.attributes.collect{case n:RichNumberAttribute => n}
    value <- mbean.getLong(attr)
    } {
    println(s"${mbean.name} - ${attr.name} = ${value}")
  }
}

```

##jmxgrep script

This scala script search matching mbean name, attribute name, or value satisfying the given set of regular exception. This jmxgrep script can be tested against itself : "jmxgrep - vendor version"

```scala
#!/bin/sh
exec java -jar jajmx.jar "$0" "$@"
!#

import jajmx._

if (args.size == 0) {
  println("Usage   : jmxgrep host port - searchMask1 ... searchMaskN")
  println("  if no args given so now let's connecting to myself, and list my mbeans...") 
}

val (options, masks) = args.toList match {
  case host::port::"-"::masks => 
            (Some(JMXOptions(host,port.toInt)), masks.map{s=>("(?i)"+s).r})
  case "-"::masks => (None, masks.map{s=>("(?i)"+s).r})
  case _ => (None,List.empty[util.matching.Regex])
}

def truncate(str:String, n:Int=60) = {
  val nonl=str.replaceAll("\n", " ").replaceAll("\r", "")
  if (nonl.size>n) nonl.take(n)+"..." else nonl
}

JMX.once(options) { jmx =>
  for {
     mbean <- jmx.mbeans
     attr  <- mbean.attributes
     value <- mbean.getString(attr) } {
     
    val found = List(mbean.name, attr.name, value).exists{item =>
       masks.exists{_.findFirstIn(item).isDefined }
    }
    if (masks.isEmpty || found) 
      println(s"${mbean.name} - ${attr.name} = ${truncate(value)}")
  }
}
```

##lsthreads script

Connect to a remote JVM using just the host adress and the used JMX port, and then list all active threads and their current states.

```scala
#!/bin/sh
exec java -jar jajmx.jar "$0" "$@"
!#
import jajmx._

val options = args.toList match {
  case host::port::_ => Some(JMXOptions(host,port.toInt))
  case _ => None
}

JMX.once(options) { jmx =>
  for (dump <- jmx.threadsDump(0)) {
    val threads = dump.threads
    val countByState = 
        threads
          .groupBy(_.status)
          .map{ case (state,sublist) => state -> sublist.size}
          .map{ case (state,count) => state+":"+count}
          .mkString(" ")
    
    println("Total %d threads,  %s".format(threads.size, countByState))
    for ( ti <- threads sortBy {_.id } ) {
      println("%d - %s - %s".format(ti.id, ti.status, ti.name) )
    }
  }
}
```


#JMX Configuration Notes

##Default JSR160 Configuration

```sh
JAVA_OPTS=$JAVA_OPTS" -Dcom.sun.management.jmxremote"
JAVA_OPTS=$JAVA_OPTS" -Dcom.sun.management.jmxremote.port=2500"
JAVA_OPTS=$JAVA_OPTS" -Dcom.sun.management.jmxremote.authenticate=false"
JAVA_OPTS=$JAVA_OPTS" -Dcom.sun.management.jmxremote.ssl=false"
JAVA_OPTS=$JAVA_OPTS" -Djava.rmi.server.hostname=192.168.0.184"
```
With authentication :
```sh
JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote.authenticate=true"

$JAVA_HOME/jre/lib/management/jmxremote.password
$JAVA_HOME/jre/lib/management/jmxremote.access
```

##FOR JBOSS 5.1

STANDARD JMX can be used to get JBOSS metrics, but unfortunately JVM metrics can't be gotten
(Interesting information : http://labs.consol.de/blog/jmx4perl/jboss-remote-jmx/)

- DO NOT ENABLE standard JMX (-Dcom.sun.management.jmxremote*)
- JVM 6 required
- The following module must be deployed : jmx-remoting.sar/
  (if not available, get one in official JBOSS packaging jboss-5.1.0.GA)
- modify the file ./server/default/deploy/jmx-remoting.sar/META-INF/jboss-service.xml to set the port 
```xml
    <?xml version="1.0" encoding="UTF-8"?>
    <server>
       <mbean code="org.jboss.mx.remoting.service.JMXConnectorServerService"
          name="jboss.remoting:service=JMXConnectorServer,protocol=rmi"
          display-name="JMX Connector Server (RMI)">
               <attribute name="BindAddress">10.104.65.101</attribute>
                <attribute name="RegistryPort">1090</attribute>
       </mbean>
    </server>
```

##For JBOSS `<` 7.x  & `>=` 6

```sh
JAVA_OPTS=$JAVA_OPTS" -Dcom.sun.management.jmxremote"
JAVA_OPTS=$JAVA_OPTS" -Djboss.platform.mbeanserver"
JAVA_OPTS=$JAVA_OPTS" -Djavax.management.builder.initial=org.jboss.system.server.jmx.MBeanServerBuilderImpl"
JAVA_OPTS=$JAVA_OPTS" -Dcom.sun.management.jmxremote.port=2500"
JAVA_OPTS=$JAVA_OPTS" -Dcom.sun.management.jmxremote.authenticate=false"
JAVA_OPTS=$JAVA_OPTS" -Dcom.sun.management.jmxremote.ssl=false"
```

TAKE CARE, to avoid error messages (ignored by processing), add jboss client jars to classpath


##For Jetty
Use standard JSR 160 jmxrmi connection, but jetty mbeans must be enabled as by
adding such block in jetty.xml configuration file, and setting all "StatsOn" to true : 
 
```xml
<Call id="MBeanServer" class="java.lang.management.ManagementFactory" name="getPlatformMBeanServer"/>

<Get id="Container" name="container">
  <Call name="addEventListener">
    <Arg>
      <New class="org.mortbay.management.MBeanContainer">
        <Arg><Ref id="MBeanServer"/></Arg>
        <Call name="start" />
      </New>
    </Arg>
  </Call>
</Get>
```

works at least for jetty 6.1.x. jetty-management.jar must be added to the classpath if not already present.


##For JBOSS 7.x

jboss-client.jar must be added to the classpath

To specify the right management listening network interface
```sh
 JAVA_OPTS="$JAVA_OPTS -Djboss.bind.address.management=10.134.115.167"
```

```
test@testhost ~/servers/jboss/bin $ ./add-user.sh 

What type of user do you wish to add? 
 a) Management User (mgmt-users.properties) 
 b) Application User (application-users.properties)
(a): a

Enter the details of the new user to add.
Realm (ManagementRealm) : 
Username : admin
Password : 
Re-enter Password : 
The username 'admin' is easy to guess
Are you sure you want to add user 'admin' yes/no? yes
About to add user 'admin' for realm 'ManagementRealm'
Is this correct yes/no? yes
Added user 'admin' to file '/opt/servers/jboss-as-7.1.1.Final/standalone/configuration/mgmt-users.properties'
Added user 'admin' to file '/opt/servers/jboss-as-7.1.1.Final/domain/configuration/mgmt-users.properties'
```

###Small checks with JBOSS 7

####using jconsole : 
```
  ./jboss-as-7.1.1.Final/bin/jconsole.sh
     service:jmx:remoting-jmx://10.134.115.167:9999    username = admin  password = ""

  if local jboss : 
  jconsole -J-Djava.class.path=/home/dcr/.gentoo/java-config-2/current-user-vm/lib/jconsole.jar:/home/dcr/.gentoo/java-config-2/current-user-vm/lib/tools.jar:/home/dcr/servers/jboss-as-7.1.1.Final/lib/jboss-client.jar
```

####using JAJMX : 
```
 $ java -classpath ./bin/client/jboss-client.jar:/opt/analysis/analysis.jar com.orange.analysis.Main
 Welcome to Scala version 2.10.0 (Java HotSpot(TM) 64-Bit Server VM, Java 1.7.0_09).
 Type in expressions to have them evaluated.
 Type :help for more information.

 scala> val jmx = jajmx.JMX("10.134.115.167", 9999, Some("admin"))
 jmx: fr.janalyse.jmx.JMX = fr.janalyse.jmx.JMXclassicalImpl@7ccea5c6

 scala> jmx.whoami
 res0: Option[fr.janalyse.jmx.ASSignature] = Some(JBossSignature(7.1.1.Final,/home/dcr/servers/jboss-as-7.1.1.Final/standalone,jboss.as))
```

