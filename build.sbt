name := "janalyse-jmx"

version := "0.7.3-SNAPSHOT"

organization :="fr.janalyse"

organizationHomepage := Some(new URL("http://www.janalyse.fr"))

scalaVersion := "2.11.7"

crossScalaVersions := Seq("2.10.5", "2.11.7")

//scalacOptions ++= Seq( "-deprecation", "-unchecked", "-feature", "-language:implicitConversions", "-language:reflectiveCalls")

libraryDependencies ++= Seq(
//    "org.json4s"                    %% "json4s-native"       % "3.2.9",
//    "org.apache.httpcomponents"      % "httpclient"          % "4.3.3",
//    "com.typesafe.scala-logging"    %% "scala-logging-slf4j" % "2.1.2",
    "org.slf4j"                      % "slf4j-api"           % "1.7.12",
    "org.scalatest"                 %% "scalatest"           % "2.2.+" % "test",
    "junit"                          % "junit"               % "4.11"  % "test"
)

resolvers += "JAnalyse Repository requirements" at "http://www.janalyse.fr/repository/"


initialCommands in console := """
    |import fr.janalyse.jmx._
    |import javax.management.ObjectName
    |//import org.json4s._
    |//import org.json4s.native.JsonMethods._
    |//import org.json4s.JsonDSL._
    |//val jmx=JMX("localhost", 8080).asInstanceOf[JMXjolokiaImpl]
    |""".stripMargin
