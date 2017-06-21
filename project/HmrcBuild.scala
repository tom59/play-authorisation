import sbt.Keys._
import sbt._
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.versioning.SbtGitVersioning


object HmrcBuild extends Build {

  import BuildDependencies._
  import uk.gov.hmrc.DefaultBuildSettings._

  val appName = "play-authorisation"

  resolvers += Resolver.bintrayRepo("jcenter", "releases")
  resolvers += Resolver.bintrayRepo("hmrc", "releases")

  lazy val playAuthorisation = (project in file("."))
    .enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning)
    .settings(
      name := appName,
      scalaVersion := "2.11.7",
      libraryDependencies ++= Seq(
        Compile.playFramework,
        Compile.playWS,
        Compile.httpVerbs,
        Compile.ficus,
        Test.scalaTest,
        Test.pegdown,
        Test.playTest,
        Test.mockito
      ),
      Developers()
    )
}

private object BuildDependencies {

  import play.sbt.PlayImport._
  import play.core.PlayVersion

  object Compile {
    val playFramework = "com.typesafe.play" %% "play" % PlayVersion.current % "provided"
    val playWS = ws % "provided"
    val httpVerbs = "uk.gov.hmrc" %% "http-verbs" % "6.4.0" % "provided"
    val ficus = "net.ceedubs" %% "ficus" % "1.1.1"
  }

  sealed abstract class Test(scope: String) {
    val scalaTest = "org.scalatest" %% "scalatest" % "2.2.4" % scope
    val pegdown = "org.pegdown" % "pegdown" % "1.5.0" % scope
    val playTest = "com.typesafe.play" %% "play-test" % PlayVersion.current % scope
    val mockito = "org.mockito" % "mockito-core" % "1.9.0" % scope
  }

  object Test extends Test("test")

}

object Developers {

  def apply() = developers := List[Developer]()
}
