name := "Decouple"

version := "1.0"

scalaVersion := "2.12.6"

resolvers += Resolver.jcenterRepo

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.5.7",
  "com.typesafe.akka" %% "akka-persistence" % "2.5.7",
  "com.github.dnvriend" %% "akka-persistence-inmemory" % "2.4.17.1",
  "org.scalatest" %% "scalatest" % "3.0.5" % "test",
  "org.scalacheck" %% "scalacheck" % "1.14.0" % "test"
)

