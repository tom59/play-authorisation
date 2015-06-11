/*
 * Copyright 2015 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.play.auth.controllers

import play.api.libs.json._
import uk.gov.hmrc.play.auth.controllers.LevelOfAssurance.LevelOfAssurance

import scala.util.matching.Regex


object LevelOfAssurance extends Enumeration {
  type LevelOfAssurance = Value
  val LOA_1 = Value("1")
  val LOA_1_5 = Value("1.5")
  val LOA_2 = Value("2")

  implicit val format = new Format[LevelOfAssurance] {

    override def reads(json: JsValue): JsResult[LevelOfAssurance] = json match {
      case JsString(v) => try {
        JsSuccess(LevelOfAssurance.withName(v))
      } catch {
        case e: NoSuchElementException => JsError(s"Invalid value for LevelOfAssurance: '$v'")
      }
      case _ => JsError("String value expected")
    }

    override def writes(v: LevelOfAssurance): JsValue = JsString(v.toString)
  }
}
case class AuthConfig(mode: String = "identity",
                      pattern: Regex = AuthConfig.defaultPatternRegex,
                      anonymousLoginPattern: Regex = AuthConfig.defaultAnonymousPatternRegex,
                      servicePrefix: String = "",
                      account: Option[String] = None,
                      agentRole: Option[String] = None,
                      delegatedAuthRule: Option[String] = None,
                      levelOfAssurance: LevelOfAssurance)

object AuthConfig {
  val defaultPatternRegex = "/([\\w]+)/([^/]+)/?.*".r
  val defaultAnonymousPatternRegex = "/([^/]+)/?.*".r
}

trait AuthParamsControllerConfig {

  import uk.gov.hmrc.play.auth.controllers.LevelOfAssurance._
  import com.typesafe.config.Config
  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.{StringReader, ValueReader}

  def controllerConfigs: Config

  private lazy val assignedDefaultLOA: Option[LevelOfAssurance] = controllerConfigs.getAs[LevelOfAssurance]("defaultLevelOfAssurance")
  private lazy val globalLOA = assignedDefaultLOA.getOrElse(LevelOfAssurance.LOA_2)

  private implicit val regexValueReader: ValueReader[Regex] = StringReader.stringValueReader.map(_.r)
  private implicit val loaValueReader: ValueReader[LevelOfAssurance] = StringReader.stringValueReader map LevelOfAssurance.withName


  private implicit val authConfigReader = ValueReader.relative[AuthConfig] { config: Config =>
    AuthConfig(
      mode = config.getAs[String]("mode").getOrElse("identity"),
      pattern = config.getAs[Regex]("pattern").getOrElse(AuthConfig.defaultPatternRegex),
      anonymousLoginPattern = config.getAs[Regex]("anonymous.pattern").getOrElse(AuthConfig.defaultAnonymousPatternRegex),
      servicePrefix = config.getAs[String]("servicePrefix").getOrElse(""),
      account = config.getAs[String]("account"),
      agentRole = config.getAs[String]("agentRole"),
      delegatedAuthRule = config.getAs[String]("delegatedAuthRule"),
      levelOfAssurance = config.getAs[LevelOfAssurance]("levelOfAssurance").getOrElse(globalLOA)
    )
  }

  def authConfig(controllerName: String): AuthConfig = {
    controllerConfigs.as[Option[AuthConfig]](s"$controllerName.authParams").getOrElse(AuthConfig(levelOfAssurance = globalLOA))
  }
}
