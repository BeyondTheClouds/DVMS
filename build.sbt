import AssemblyKeys._

name := "DVMS"

version := "0.1"

organization := "dvms"

scalaVersion := "2.10.0"

crossPaths := false

retrieveManaged := true

resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases"

resolvers += "Typesafe Snapshots" at "http://repo.typesafe.com/typesafe/snapshots"

//resolvers += "Entropy release" at "http://btrcloud.org/maven"

//resolvers += "Choco release" at "http://www.emn.fr/z-info/choco-repo/mvn/repository/"

libraryDependencies += "org.scalatest" % "scalatest_2.10" % "1.9.1" % "test"

//libraryDependencies += "com.typesafe.akka" %% "akka-remote" % "2.2-SNAPSHOT"

//libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.2-SNAPSHOT"

libraryDependencies += "junit" % "junit" % "4.8.1" % "test"

//libraryDependencies += "choco" % "choco" % "2.1.4" % "test"

//libraryDependencies += "entropy" % "entropy-api" % "2.1.14" % "test"

//libraryDependencies += "entropy" % "entropy-core" % "2.1.14" % "test"

//libraryDependencies += "entropy" % "entropy" % "2.1.14" % "test"

seq(assemblySettings: _*)

mainClass in assembly := Some("dvms.Main")

test in assembly := {}

jarName in assembly := "dvms.jar"
