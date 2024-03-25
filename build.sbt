import com.ossuminc.sbt.OssumIncPlugin
import com.ossuminc.sbt.helpers.Publishing
import com.ossuminc.sbt.helpers.RootProjectInfo.Keys.{gitHubOrganization, gitHubRepository}

Global / onChangedBuildSource := ReloadOnSourceChanges

enablePlugins(OssumIncPlugin)

lazy val root =
  Root("root", "riddl-hugo", "2024")
    .configure(Publishing.configure, With.git, With.dynver)
    .settings(
      ThisBuild / gitHubRepository := "riddl",
      ThisBuild / gitHubOrganization := "ossuminc",
    )
    .aggregate(diagrams, hugo)

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

lazy val diagrams = Module("diagrams", "riddl-hugo-diagrams")
  .configure(With.typical, With.coverage(90.0))
  .settings(libraryDependencies ++= Deps.riddl)
