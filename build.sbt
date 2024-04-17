import com.ossuminc.sbt.OssumIncPlugin
import com.ossuminc.sbt.helpers.RootProjectInfo.Keys.{gitHubOrganization, gitHubRepository}

Global / onChangedBuildSource := ReloadOnSourceChanges

enablePlugins(OssumIncPlugin)

lazy val hugo =
  Root(
    "riddl-hugo",
    "ossuminc",
    "com.ossuminc.hugo",
    "Ossum, Inc.",
    startYr = 2024
  )
    .configure(With.typical, With.coverage(50), With.publishing, With.scalafmt)
    .settings(
      scalaVersion := "3.4.1",
      description := "The hugo command turns a RIDDL AST into source input for hugo static site generator",
      Compile / unmanagedResourceDirectories += {
        baseDirectory.value / "resources"
      },
      Test / parallelExecution := false,
      libraryDependencies ++= Deps.pureconfig ++ Deps.riddl
    )
