ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.1.3"

lazy val root = (project in file("."))
  .settings(
    name := "surfsup"
  )


lazy val commonSettings = Seq(
  organization := "com.pureblocks.surfsup",
  scalaVersion := "3.1.3"
)

lazy val rootProject = (project in file("."))
  .settings(commonSettings: _*)
  .settings(publishArtifact := false, name := "surfsup")
  .aggregate(beaches)
lazy val commons: Project = (project in file("commons"))
  .settings(commonSettings: _*)
  .settings(
    name := "commons",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % "2.0.0"
    )
  )
lazy val beaches: Project = (project in file("beaches"))
  .settings(commonSettings: _*)
  .settings(
    name := "beaches",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % "2.0.0",
      "com.softwaremill.sttp.client3" %% "zio" % "3.7.4",
      "dev.zio" %% "zio-json" % "0.3.0-RC10"
    )
  ).dependsOn(commons)
