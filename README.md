
#JMX Configuration Notes#

##Default JSR160 Configuration##

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

##FOR JBOSS 5.1## 

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

##For JBOSS <7.x  & >=6##

```sh
JAVA_OPTS=$JAVA_OPTS" -Dcom.sun.management.jmxremote"
JAVA_OPTS=$JAVA_OPTS" -Djboss.platform.mbeanserver"
JAVA_OPTS=$JAVA_OPTS" -Djavax.management.builder.initial=org.jboss.system.server.jmx.MBeanServerBuilderImpl"
JAVA_OPTS=$JAVA_OPTS" -Dcom.sun.management.jmxremote.port=2500"
JAVA_OPTS=$JAVA_OPTS" -Dcom.sun.management.jmxremote.authenticate=false"
JAVA_OPTS=$JAVA_OPTS" -Dcom.sun.management.jmxremote.ssl=false"
```

TAKE CARE, to avoid error messages (ignored by processing), add jboss client jars to classpath


##For Jetty##
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


##For JBOSS 7.x##

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

Small checks with JBOSS 7:

using jconsole : 
```
  ./jboss-as-7.1.1.Final/bin/jconsole.sh
     service:jmx:remoting-jmx://10.134.115.167:9999    username = admin  password = ""

  if local jboss : 
  jconsole -J-Djava.class.path=/home/dcr/.gentoo/java-config-2/current-user-vm/lib/jconsole.jar:/home/dcr/.gentoo/java-config-2/current-user-vm/lib/tools.jar:/home/dcr/servers/jboss-as-7.1.1.Final/lib/jboss-client.jar
```

using JAJMX : 
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

