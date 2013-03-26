import AssemblyKeys._

name := "DVMS"

version := "0.1"

organization := "dvms"

scalaVersion := "2.10.0"

crossPaths := false

retrieveManaged := true

resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases"

resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/snapshots"

libraryDependencies += "org.scalatest" % "scalatest_2.10" % "1.9.1" % "test"

libraryDependencies += "com.typesafe.akka" %% "akka-remote" % "2.2-SNAPSHOT"

libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.2-SNAPSHOT"

libraryDependencies += "junit" % "junit" % "4.8.1" % "test"

seq(assemblySettings: _*)

mainClass in assembly := Some("dvms.Main")

jarName in assembly := "dvms.jar"
