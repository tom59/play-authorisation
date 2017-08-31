/*
 * Copyright 2017 HM Revenue & Customs
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

import org.scalatest.{Matchers, WordSpecLike}
import uk.gov.hmrc.play.auth.microservice.connectors.ConfidenceLevel

class AuthConfigSpec extends WordSpecLike with Matchers {

  import com.typesafe.config.{Config, ConfigFactory}
  import net.ceedubs.ficus.Ficus._

  val config = ConfigFactory.parseString(
    """
      |controllers {
      |  confidenceLevel = 0
      |  com.kenshoo.play.metrics.MetricsController {
      |    needsAuditing = false
      |  }
      |  com.kenshoo.play.metrics.AnotherController {
      |    needsAuth = true
      |    authParams = {
      |      pattern = "/(\\w*)/(\\d)/.*"
      |    }
      |  }
      |  com.kenshoo.play.metrics.ControllerWithDodgyPattern {
      |    authParams = {
      |      pattern = "*.*"
      |    }
      |  }
      |  uk.gov.hmrc.play.controllers.DelegateAuthController {
      |    needsAuth = true
      |    authParams = {
      |      pattern = "/(\\w*)/(\\d)/.*"
      |      account = agent
      |      agentRole = admin
      |      delegatedAuthRule = lp-paye
      |    }
      |  }
      |  uk.gov.hmrc.play.controllers.AbsentDelegateAuthController {
      |    needsAuth = true
      |    authParams = {
      |      pattern = "/(\\w*)/(\\d)/.*"
      |      confidenceLevel = 200
      |    }
      |  }
      |}
    """.stripMargin)


  val InvalidConfig = ConfigFactory.parseString(
    """
      |controllers {
      |  uk.gov.hmrc.play.controllers.AbsentDelegateAuthController {
      |    needsAuth = true
      |    authParams = {
      |      pattern = "/(\\w*)/(\\d)/.*"
      |      confidenceLevel = 3333
      |    }
      |  }
      |}
    """.stripMargin)

  val DefaultCL = ConfigFactory.parseString(
    """
      |controllers {
      |  confidenceLevel = 100
      |}
    """.stripMargin)


  val InvalidDefaultCL = ConfigFactory.parseString(
    """
      |controllers {
      |  confidenceLevel = 56
      |}
    """.stripMargin)


  val NoCL = ConfigFactory.parseString(
    """
      |controllers {
      |  com.kenshoo.play.metrics.MetricsController {
      |    needsAuditing = true
      |    authParams = {
      |      pattern = "/(\\w*)/(\\d)/.*"
      |    }
      |  }
      |}
    """.stripMargin)

  val cc = new AuthParamsControllerConfig {
    lazy val controllerConfigs = config.as[Config]("controllers")
  }

  val ccForDefaultConfidenceLevel = new AuthParamsControllerConfig {
    lazy val controllerConfigs = DefaultCL.as[Config]("controllers")
  }

  "controller config" should {
    "return the default auth pattern if none is configured" in {
      cc.authConfig("com.kenshoo.play.metrics.MetricsController").pattern.toString() should be("/([\\w]+)/([^/]+)/?.*")
    }
    "return the default auth anonymous pattern if none is configured" in {
      cc.authConfig("com.kenshoo.play.metrics.MetricsController").anonymousLoginPattern.toString() should be("/([^/]+)/?.*")
    }
    "return the default auth mode if none is configured" in {
      cc.authConfig("com.kenshoo.play.metrics.MetricsController").mode should be("identity")
    }
    "return the configured auth pattern" in {
      cc.authConfig("com.kenshoo.play.metrics.AnotherController").pattern.toString() should be("/(\\w*)/(\\d)/.*")
    }
    "throw an exception if the pattern does not compile" in {
      an[Exception] should be thrownBy cc.authConfig("com.kenshoo.play.metrics.ControllerWithDodgyPattern")
    }
  }

  "controller auth config" should {

    "return the configured account" in {
      val config = cc.authConfig("uk.gov.hmrc.play.controllers.DelegateAuthController")
      config.account shouldBe Some("agent")
      config.agentRole shouldBe Some("admin")
      config.delegatedAuthRule shouldBe Some("lp-paye")
    }

    "return None for elements of authParams not configured" in {
      val config = cc.authConfig("uk.gov.hmrc.play.controllers.AbsentDelegateAuthController")
      config.account shouldBe None
      config.agentRole shouldBe None
      config.delegatedAuthRule shouldBe None
    }

    "throw error when confidenceLevel is not set either at global  or controller level " in {
      val cc = new AuthParamsControllerConfig {
        lazy val controllerConfigs = NoCL.as[Config]("controllers")
      }
      an[Exception] shouldBe thrownBy {
        cc.authConfig("com.kenshoo.play.metrics.MetricsController")
      }
    }

    "set confidenceLevel to 100  at global level" in {
      val config = ccForDefaultConfidenceLevel.authConfig("uk.gov.hmrc.play.controllers.DelegateAuthController")
      config.confidenceLevel shouldBe ConfidenceLevel.L100
    }

    "set confidenceLevel to 200  for the specific controller" in {
      val config = cc.authConfig("uk.gov.hmrc.play.controllers.AbsentDelegateAuthController")
      config.confidenceLevel shouldBe ConfidenceLevel.L200
    }
  }

  "Invalid ConfidenceLevel values" should {
    "be rejected when defined at global level" in {
      val ccForDefaultConfidenceLevel = new AuthParamsControllerConfig {
        lazy val controllerConfigs = InvalidDefaultCL.as[Config]("controllers")
      }
      an[Exception] shouldBe thrownBy {
        ccForDefaultConfidenceLevel.authConfig("uk.gov.hmrc.play.controllers.DelegateAuthController")
      }
    }

    "be rejected when defined at the controller level" in {
      val cc = new AuthParamsControllerConfig {
        lazy val controllerConfigs = InvalidConfig.as[Config]("controllers")
      }
      an[Exception] shouldBe thrownBy {
        val conf = cc.authConfig("uk.gov.hmrc.play.controllers.AbsentDelegateAuthController")
      }
    }

  }
}
