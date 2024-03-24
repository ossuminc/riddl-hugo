import com.ossuminc.sbt.OssumIncPlugin
import com.ossuminc.sbt.helpers.RootProjectInfo.Keys.{
  gitHubOrganization,
  gitHubRepository
}


Global / onChangedBuildSource := ReloadOnSourceChanges

enablePlugins(OssumIncPlugin)

lazy val root =
  Root("root", "riddl-hugo", "2024")
    .settings(
      ThisBuild / gitHubRepository := "riddl",
      ThisBuild / gitHubOrganization := "ossuminc",
      publish / skip := true
    )
    .aggregate(hugo, themes, diagrams)

lazy val hugo: Project = Module("hugo", "riddl-hugo")
  .configure(With.typical, With.coverage(50))
  .settings(
    description := "The hugo command turns a RIDDL AST into source input for hugo static site generator",
    Compile / unmanagedResourceDirectories += {
      baseDirectory.value / "resources"
    },
    Test / parallelExecution := false,
    libraryDependencies ++= Deps.pureconfig ++ Deps.riddl
  )
  .dependsOn(diagrams)

lazy val themes = Module("themes", "riddl-hugo-themes")
  .configure(With.typical, With.coverage(90.0))
  .dependsOn(hugo)

lazy val diagrams = Module("diagrams", "riddl-hugo-diagrams")
  .configure(With.typical, With.coverage(90.0))
  .settings(libraryDependencies ++= Deps.riddl)
