name := """auction-manager"""
organization := "andycot"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.11"

// https://index.scala-lang.org/okumin/akka-persistence-sql-async

libraryDependencies ++= Seq(
  filters,
  "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.0" % Test,
  "com.typesafe.akka" %% "akka-persistence" % "2.5.1",
  "com.okumin" %% "akka-persistence-sql-async" % "0.4.0",
  "com.github.mauricio" %% "mysql-async" % "0.2.20"
)
