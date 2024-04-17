import sbt.*

object Deps {

  /** V - Dependency Versions object */
  object V {
    val config = "1.4.1"
    val lang3 = "3.12.0"
    val pureconfig = "0.17.5"
    val riddl = "0.43.0-1-bf64a02e-20240416-2153"
    val scalacheck = "1.15.4"
    val scalatest = "3.2.9"
    val scopt = "4.1.0"
  }

  val riddl: Seq[ModuleID] = Seq(
    "com.ossuminc" %% "riddl-utils" % V.riddl,
    "com.ossuminc" %% "riddl-language" % V.riddl,
    "com.ossuminc" %% "riddl-passes" % V.riddl,
    "com.ossuminc" %% "riddl-analyses" % V.riddl,
    "com.ossuminc" %% "riddl-commands" % V.riddl,
    "com.ossuminc" %% "riddl-testkit" % V.riddl % Test
  )

  val pureconfig: Seq[ModuleID] =
    Seq[ModuleID]("com.github.pureconfig" %% "pureconfig-core" % V.pureconfig)
}
