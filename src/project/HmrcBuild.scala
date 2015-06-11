import sbt.Keys._
import sbt._
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.versioning.SbtGitVersioning


object HmrcBuild extends Build {

  import BuildDependencies._
  import uk.gov.hmrc.DefaultBuildSettings._

  val appName = "play-authorisation"

  lazy val playAuthorisation = (project in file("."))
    .enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning)
    .settings(
      name := appName,
      targetJvm := "jvm-1.7",
      libraryDependencies ++= Seq(
        Compile.playFramework,
        Compile.playWS,
        Compile.httpVerbs,
        Compile.domain,
        Test.scalaTest,
        Test.pegdown,
        Test.playTest,
        Test.httpVerbs
      ),
      Developers()
    )
}

private object BuildDependencies {

  import play.PlayImport._
  import play.core.PlayVersion

  val httpVerbsVersion = "1.5.0"

  object Compile {
    val playFramework = "com.typesafe.play" %% "play" % PlayVersion.current
    val playWS = ws % "provided"
    val httpVerbs = "uk.gov.hmrc" %% "http-verbs" % httpVerbsVersion
    val domain = "uk.gov.hmrc" %% "domain" % "2.6.0"
  }

  sealed abstract class Test(scope: String) {
    val scalaTest = "org.scalatest" %% "scalatest" % "2.2.4" % scope
    val pegdown = "org.pegdown" % "pegdown" % "1.5.0" % scope
    val playTest = "com.typesafe.play" %% "play-test" % PlayVersion.current % scope
    val httpVerbs = "uk.gov.hmrc" %% "http-verbs" % httpVerbsVersion % scope classifier "tests"
  }

  object Test extends Test("test")

}

object Developers {

  def apply() = developers := List[Developer]()
}
