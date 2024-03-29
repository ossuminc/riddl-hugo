import com.ossuminc.sbt.OssumIncPlugin
import com.ossuminc.sbt.helpers.RootProjectInfo.Keys.{
  gitHubOrganization,
  gitHubRepository
}

Global / onChangedBuildSource := ReloadOnSourceChanges

enablePlugins(OssumIncPlugin)

lazy val `riddl-hugo` =
  Root(
    "riddl-hugo",
    "ossuminc",
    "com.ossuminc.hugo",
    "Ossum, Inc.",
    startYr = 2024
  ).configure(With.noPublishing, With.git, With.dynver)
    .aggregate(diagrams, hugo)

lazy val hugo: Project = Module("hugo", "riddl-hugo")
  .configure(With.typical, With.coverage(50), With.publishing, With.scalafmt)
  .settings(
    description := "The hugo command turns a RIDDL AST into source input for hugo static site generator",
    Compile / unmanagedResourceDirectories += {
      baseDirectory.value / "resources"
    },
    Test / parallelExecution := false,
    libraryDependencies ++= Deps.pureconfig ++ Deps.riddl
  )
  .dependsOn(diagrams)

lazy val diagrams = Module("diagrams", "riddl-hugo-diagrams")
  .configure(With.typical, With.coverage(90.0), With.publishing)
  .settings(libraryDependencies ++= Deps.riddl)
