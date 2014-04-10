name := "janalyse-jmx"

version := "0.7.0-beta"

organization :="fr.janalyse"

organizationHomepage := Some(new URL("http://www.janalyse.fr"))

scalaVersion := "2.10.4"

//crossScalaVersions := Seq("2.10.2")

libraryDependencies ++= Seq( 
    "com.typesafe"                  %% "scalalogging-slf4j"  % "1.0.1",
    "ch.qos.logback" % "logback-classic" % "1.0.3",
    "org.json4s"                    %% "json4s-native"       % "3.2.8",
    "org.apache.httpcomponents"      % "httpclient"          % "4.3.3",
//    "org.jolokia"                    % "jolokia-client-java" % "1.2.0",
    "org.scalatest"                 %% "scalatest"           % "1.9.1" % "test",
    "junit"                          % "junit"               % "4.10"  % "test"
)

resolvers += "JAnalyse Repository requirements" at "http://www.janalyse.fr/repository/"

publishTo := Some(
     Resolver.sftp(
         "JAnalyse Repository",
         "www.janalyse.fr",
         "/home/tomcat/webapps-janalyse/repository"
     ) as("tomcat", new File(util.Properties.userHome+"/.ssh/id_rsa"))
)

//publishArtifact in packageDoc := false

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

// Temporary hack to have scaladoc work fine : check https://github.com/harrah/xsbt/issues/85
unmanagedClasspath in Compile += Attributed.blank(new java.io.File("doesnotexist"))


initialCommands in console := """
import fr.janalyse.jmx._
"""
