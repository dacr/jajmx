name := "janalyse-jmx"
organization :="fr.janalyse"
homepage := Some(new URL("https://github.com/dacr/jajmx"))
licenses += "Apache 2" -> url(s"http://www.apache.org/licenses/LICENSE-2.0.txt")
scmInfo := Some(ScmInfo(url(s"https://github.com/dacr/jajmx"), s"git@github.com:dacr/jajmx.git"))

scalaVersion := "2.13.1"
scalacOptions ++= Seq( "-deprecation", "-unchecked", "-feature", "-language:implicitConversions", "-language:reflectiveCalls")

crossScalaVersions := Seq("2.12.11", "2.13.1")
// 2.10.7  : generates java 6 bytecodes
// 2.11.12 : generates java 6 bytecodes
// 2.12.8  : generates java 8 bytecodes && JVM8 required for compilation
// 2.13.0  : generates java 8 bytecodes && JVM8 required for compilation

libraryDependencies ++= Seq(
  "org.slf4j"                      % "slf4j-api"           % "1.7.30",
  "org.scalatest"                 %% "scalatest"           % "3.1.1" % "test"
)

testOptions in Test += {
  val rel = scalaVersion.value.split("[.]").take(2).mkString(".")
  Tests.Argument(
    "-oDF", // -oW to remove colors
    "-u", s"target/junitresults/scala-$rel/"
  )
}

initialCommands in console :=
"""
  |import fr.janalyse.jmx._
  |import javax.management.ObjectName
  |""".stripMargin

