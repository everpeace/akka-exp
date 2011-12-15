name:="ToyExperimentOfAkka"

version:="0.0.1"

scalaVersion:="2.9.1"

organization:="everpeace.org"

//scalaz
resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases"

libraryDependencies ++= Seq(
"se.scalablesolutions.akka" % "akka-actor" % "1.3-RC4"  withSources(),
"se.scalablesolutions.akka" % "akka-stm"% "1.3-RC4" withSources(),
"se.scalablesolutions.akka" % "akka-remote"% "1.3-RC4" withSources()
)

//compile options
scalacOptions ++= Seq("-unchecked", "-deprecation")