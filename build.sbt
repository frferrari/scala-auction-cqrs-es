name := """auction-manager"""
organization := "andycot"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.11"

// https://index.scala-lang.org/okumin/akka-persistence-sql-async

// Testing persistent actors http://tudorzgureanu.com/akka-persistence-testing-persistent-actors/

parallelExecution in Test := false

concurrentRestrictions in Global := Seq(
  Tags.limit(Tags.CPU, 2),
  Tags.limit(Tags.Network, 10),
  Tags.limit(Tags.Test, 1),
  Tags.limitAll( 1 )
)

// sbt-scoverage
// sbt clean coverage test
// sbt coverageReport
coverageExcludedPackages := "<empty>"
// coverageMinimum := 80
// coverageFailOnMinimum := true

resolvers += Resolver.jcenterRepo

libraryDependencies ++= Seq(
  jdbc,
  filters,
  "mysql" % "mysql-connector-java" % "5.1.36",
  "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.0" % Test,
  "com.typesafe.akka" %% "akka-persistence" % "2.5.2",
  "com.github.dnvriend" %% "akka-persistence-inmemory" % "1.3.7",
  "com.typesafe.play" %% "anorm" % "2.5.3" withSources(),

  // MongoDB
  "org.mongodb.scala" %% "mongo-scala-driver" % "2.0.0",

  // streams
  "com.typesafe.akka" %% "akka-stream" % "2.5.2",
  // akka http
  "com.typesafe.akka" %% "akka-http" % "10.0.7"
  // "com.okumin" %% "akka-persistence-sql-async" % "0.4.0",
  // "com.github.mauricio" %% "mysql-async" % "0.2.20"
)
