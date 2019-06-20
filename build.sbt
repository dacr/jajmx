name := "janalyse-jmx"

organization :="fr.janalyse"
homepage := Some(new URL("https://github.com/dacr/jajmx"))

scalaVersion := "2.9.3"
crossScalaVersions := Seq(
  "2.9.3",   // generate java 5 bytecodes, even with run with a JVM6
  "2.10.7",  // generate java 6 bytecodes
  "2.11.12", // generate java 6 bytecodes
  "2.12.4"   // generate java 8 bytecodes && JVM8 required for compilation
)

libraryDependencies ++= Seq(
    "org.slf4j"    % "slf4j-api"       % "1.7.25",
    "junit"        % "junit"           % "4.12" % "test",
    "com.novocode" % "junit-interface" % "0.11" % "test"
    //"org.scalatest"                 %% "scalatest"           % "3.0.1" % "test"
)

//testOptions += Tests.Argument(TestFrameworks.JUnit, "-q", "-v")

initialCommands in console := """
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
    releaseStepCommand("sonatypeReleaseAll")
    //pushChanges
  )
 