import com.ossuminc.sbt.OssumIncPlugin
import com.ossuminc.sbt.helpers.RootProjectInfo.Keys.{
  gitHubOrganization,
  gitHubRepository
}

Global / onChangedBuildSource := ReloadOnSourceChanges

enablePlugins(OssumIncPlugin)

lazy val root =
  Root("root", "riddl", "2024").aggregate(hugo).settings(publish / skip := true)

lazy val hugo = Module("hugo", "riddl-hugo")
  .configure(With.typical, With.coverage(90.0))
  .settings(
    ThisBuild / gitHubRepository := "riddl",
    ThisBuild / gitHubOrganization := "ossuminc",
    libraryDependencies ++= Deps.riddl
  )

lazy val themes = Module("themes", "riddl-hugo-themes")
  .configure(With.typical, With.coverage(90.0))
  .dependsOn(hugo)
