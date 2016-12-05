enablePlugins(JavaServerAppPackaging)

name := "goticks"

version := "1.0"

organization := "com.goticks" 

libraryDependencies ++= {
  val akkaVersion = "2.4.10"
  val akkaHttpVersion = "10.0.0"
  val scalaTestVersion = "2.2.0"
  Seq(
    "com.typesafe.akka" %% "akka-actor"      % akkaVersion, 
    "com.typesafe.akka" %% "akka-http-core"  % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http"  % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json"  % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-slf4j"      % akkaVersion,
    "ch.qos.logback"    %  "logback-classic" % "1.1.3",
    "com.typesafe.akka" %% "akka-testkit"    % akkaVersion   % "test",
    "org.scalatest"     %% "scalatest"       % scalaTestVersion % "test"
  )
}

// Add the dependency of swagger-akka-http
libraryDependencies +=  "com.github.swagger-akka-http" %% "swagger-akka-http" % "0.7.3" excludeAll(
  ExclusionRule(organization ="com.typesafe.akka"))

// Add the dependency of CORS
libraryDependencies += "ch.megard" %% "akka-http-cors" % "0.1.10"  excludeAll(
  ExclusionRule(organization ="com.typesafe.akka"))
