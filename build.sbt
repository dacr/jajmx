name := "janalyse-jmx"

organization :="fr.janalyse"
homepage := Some(new URL("https://github.com/dacr/jajmx"))

scalaVersion := "2.12.8"
crossScalaVersions := Seq("2.10.7", "2.11.12", "2.12.8")

//scalacOptions ++= Seq( "-deprecation", "-unchecked", "-feature", "-language:implicitConversions", "-language:reflectiveCalls")

libraryDependencies ++= Seq(
    "org.slf4j"       % "slf4j-api"    % "1.7.25",
    "org.scalatest"  %% "scalatest"    % "3.0.8" % "test"
)

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
    releaseStepCommand("sonatypeReleaseAll"),
    pushChanges
  )
 
