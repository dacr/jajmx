name := "janalyse-jmx"

organization :="fr.janalyse"
homepage := Some(new URL("https://github.com/dacr/jajmx"))

scalaVersion := "2.13.0"

// 2.9.3   : generates java 5 bytecodes, even with run with a JVM6
// 2.10.7  : generates java 6 bytecodes
// 2.11.12 : generates java 6 bytecodes
// 2.12.8  : generates java 8 bytecodes && JVM8 required for compilation
// 2.13.0  : generates java 8 bytecodes && JVM8 required for compilation

libraryDependencies ++= Seq(
  "org.slf4j"                      % "slf4j-api"           % "1.7.26",
  "org.scalatest"                 %% "scalatest"           % "3.0.8" % "test"
)

testOptions += Tests.Argument(TestFrameworks.JUnit, "-q", "-v")

initialCommands in console :=
"""
  |import fr.janalyse.jmx._
  |import javax.management.ObjectName
  |""".stripMargin



pomIncludeRepository := { _ => false }

useGpg := true

licenses += "Apache 2" -> url(s"http://www.apache.org/licenses/LICENSE-2.0.txt")
releaseCrossBuild := true
releasePublishArtifactsAction := PgpKeys.publishSigned.value
publishMavenStyle := true
publishArtifact in Test := false
publishTo := Some(if (isSnapshot.value) Opts.resolver.sonatypeSnapshots else Opts.resolver.sonatypeStaging)

scmInfo := Some(ScmInfo(url(s"https://github.com/dacr/jajmx"), s"git@github.com:dacr/jajmx.git"))

PgpKeys.useGpg in Global := true

pomExtra in Global := {
  <developers>
    <developer>
      <id>dacr</id>
      <name>David Crosson</name>
      <url>https://github.com/dacr</url>
    </developer>
  </developers>
}


import ReleaseTransformations._
releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    //runClean,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    publishArtifacts,
    setNextVersion,
    commitNextVersion,
    releaseStepCommand("sonatypeReleaseAll"),
    pushChanges
  )
 
