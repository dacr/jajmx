import AssemblyKeys._

seq(assemblySettings: _*)

name := "janalyse-jmx-onejar"

scalaVersion := "2.10.2"

mainClass in assembly := Some("JAJMXLauncher")

jarName in assembly := "jajmx.jar"

libraryDependencies ++= Seq(
  "fr.janalyse"                   %% "janalyse-jmx"  % "0.6.4"
 ,"com.github.scala-incubator.io" %% "scala-io-core" % "0.4.2"
 ,"com.github.scala-incubator.io" %% "scala-io-file" % "0.4.2"
)


libraryDependencies <++=  scalaVersion { sv =>
       ("org.scala-lang" % "jline"           % sv  % "compile")  ::
       ("org.scala-lang" % "scala-compiler"  % sv  % "compile")  ::
       ("org.scala-lang" % "scalap"          % sv  % "compile")  ::Nil
}

resolvers += "JAnalyse Repository" at "http://www.janalyse.fr/repository/"

// jansi is embedded inside jline !
excludedJars in assembly <<= (fullClasspath in assembly) map { cp =>
  cp filter {c=> List("jansi") exists {c.data.getName contains _} }
  }

// ------------------------------------------------------
sourceGenerators in Compile <+=
 (sourceManaged in Compile, version, name, jarName in assembly) map {
  (dir, version, projectname, jarexe) =>
  val file = dir / "fr" / "janalyse" / "jmx" / "MetaInfo.scala"
  IO.write(file,
  """package fr.janalyse.jmx
    |object MetaInfo {
    |  val version="%s"
    |  val projectName="%s"
    |  val jarbasename="%s"
    |}
    |""".stripMargin.format(version, projectname, jarexe.split("[.]").head) )
  Seq(file)
}
