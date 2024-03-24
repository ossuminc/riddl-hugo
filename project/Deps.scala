import sbt.*

object Deps {

  /** V - Dependency Versions object */
  object V {
    val akka = "2.7.0"
    val config = "1.4.1"
    val lang3 = "3.12.0"
    val pureconfig = "0.17.1"
    val riddl = "0.40.0"
    val scalacheck = "1.15.4"
    val scalatest = "3.2.9"
    val scopt = "4.1.0"
  }

  val riddl: Seq[ModuleID] = Seq(
    "com.ossuminc" %% "riddl-language" % V.riddl,
    "com.ossuminc" %% "riddl-commands" % V.riddl,
    "com.ossuminc" %% "riddl-diagrams" % V.riddl,
    "com.ossuminc" %% "riddl-stats" % V.riddl,
    "com.ossuminc" %% "riddl-testkit" % V.riddl % Test
  )

  val akka_typed: Seq[ModuleID] = Seq(
    "com.typesafe.akka" %% "akka-actor-typed" % V.akka,
    "com.typesafe.akka" %% "akka-actor-testkit-typed" % V.akka % Test,
    "org.scalatest" %% "scalatest" % V.scalatest
  )

  val akka_streams: Seq[ModuleID] = Seq(
    "com.typesafe.akka" %% "akka-stream" % V.akka,
    "com.typesafe.akka" %% "akka-stream-testkit" % V.akka % Test
  )

}
