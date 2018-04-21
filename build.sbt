name := "ImgurBot"

version := "0.1"

scalaVersion := "2.12.5"

resolvers += Resolver.sonatypeRepo("staging")

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.5.12"
libraryDependencies += "info.mukel" %% "telegrambot4s-core" % "3.1.0-RC1"
libraryDependencies +=  "org.scalaj" %% "scalaj-http" % "2.3.0"
libraryDependencies += "org.json4s" %% "json4s-jackson" % "3.6.0-M3"
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3" % Runtime

resolvers += "Akka Snapshots" at "http://repo.akka.io/snapshots/"