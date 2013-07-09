import AssemblyKeys._

name := "DVMS"

version := "0.1"

organization := "org.discovery"

scalaVersion := "2.10.0"

crossPaths := false

retrieveManaged := true

resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases"

resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/snapshots"

libraryDependencies += "org.scalatest"     %% "scalatest" % "1.9.1" % "test"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.2.0-RC1"

libraryDependencies += "com.typesafe.akka" %% "akka-remote" % "2.2.0-RC1"

libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.2.0-RC1" % "test"

libraryDependencies += "com.typesafe.akka" %% "akka-multi-node-testkit" % "2.2.0-RC1" % "test"

libraryDependencies += "junit" % "junit" % "4.5" % "test"

//libraryDependencies += "choco" % "choco" % "2.1.4" % "test"

//libraryDependencies += "entropy" % "entropy-api" % "2.1.14" % "test"

//libraryDependencies += "entropy" % "entropy-core" % "2.1.14" % "test"

//libraryDependencies += "entropy" % "entropy" % "2.1.14" % "test"

seq(assemblySettings: _*)

mainClass in assembly := Some("org.discovery.dvms.Main")

test in assembly := {}

jarName in assembly := "dvms.jar"
