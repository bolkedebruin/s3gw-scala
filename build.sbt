name := "s3gw"

version := "0.1"

scalaVersion := "2.12.6"

unmanagedClasspath in Runtime += sourceDirectory.value / "conf"

libraryDependencies += "com.criteo.lolhttp" %% "lolhttp" % "0.10.1"
libraryDependencies += "org.apache.ranger" % "ranger-plugins-common" % "1.0.0"
libraryDependencies += "io.lemonlabs" %% "scala-uri" % "1.1.2"
libraryDependencies += "org.apache.logging.log4j" %% "log4j-api-scala" % "11.0"
libraryDependencies += "io.github.twonote" % "radosgw-admin4j" % "1.1.0"
libraryDependencies += "io.monix" %% "monix" % "3.0.0-RC1"