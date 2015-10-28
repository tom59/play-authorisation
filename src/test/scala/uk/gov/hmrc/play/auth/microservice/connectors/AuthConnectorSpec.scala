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

package uk.gov.hmrc.play.auth.microservice.connectors

import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpecLike}
import play.api.libs.ws.WSResponse
import play.api.test.Helpers._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class AuthConnectorSpec extends WordSpecLike with Matchers with MockitoSugar with ScalaFutures {
  val stubAuthConnector = new AuthConnector {
    override def authBaseUrl = ???

    override protected def callAuth(url: String)(implicit hc: HeaderCarrier): Future[WSResponse] = ???
  }


  val ConfidenceLevel = 100
  "ResourceToAuthorise.buildUrl" should {

    val authBaseUrl = "authBase"
    "generate a url for get authorisation" in {
      ResourceToAuthorise(HttpVerb("GET"), Regime("foo"), AccountId("testid")).buildUrl(authBaseUrl, AuthRequestParameters(ConfidenceLevel)) shouldBe s"authBase/authorise/read/foo/testid?confidenceLevel=$ConfidenceLevel"
    }
    "generate a url for head authorisation" in {
      ResourceToAuthorise(HttpVerb("HEAD"), Regime("foo"), AccountId("testid")).buildUrl(authBaseUrl, AuthRequestParameters(ConfidenceLevel)) shouldBe s"authBase/authorise/read/foo/testid?confidenceLevel=$ConfidenceLevel"
    }
    "generate a url for post authorisation" in {
      ResourceToAuthorise(HttpVerb("POST"), Regime("foo"), AccountId("testid")).buildUrl(authBaseUrl, AuthRequestParameters(ConfidenceLevel)) shouldBe s"authBase/authorise/write/foo/testid?confidenceLevel=$ConfidenceLevel"
    }
    "generate a url for put authorisation" in {
      ResourceToAuthorise(HttpVerb("PUT"), Regime("foo"), AccountId("testid")).buildUrl(authBaseUrl, AuthRequestParameters(ConfidenceLevel)) shouldBe s"authBase/authorise/write/foo/testid?confidenceLevel=$ConfidenceLevel"
    }
    "generate a url for delete authorisation" in {
      ResourceToAuthorise(HttpVerb("DELETE"), Regime("foo"), AccountId("testid")).buildUrl(authBaseUrl, AuthRequestParameters(ConfidenceLevel)) shouldBe s"authBase/authorise/write/foo/testid?confidenceLevel=$ConfidenceLevel"
    }

    "generate a url for role based agent authorisation" in {
      ResourceToAuthorise(HttpVerb("GET"), Regime("agent"), AccountId("testid")).buildUrl(authBaseUrl, AuthRequestParameters(ConfidenceLevel, agentRoleRequired = Some("admin"))) shouldBe s"authBase/authorise/read/agent/testid?confidenceLevel=$ConfidenceLevel&agentRoleRequired=admin"
    }

    "generate a url for rule based agent authorisation" in {
      ResourceToAuthorise(HttpVerb("GET"), Regime("agent"), AccountId("testid")).buildUrl(authBaseUrl, AuthRequestParameters(ConfidenceLevel, delegatedAuthRule = Some("lp-paye"))) shouldBe s"authBase/authorise/read/agent/testid?confidenceLevel=$ConfidenceLevel&delegatedAuthRule=lp-paye"
    }

    "generate a url for role and rule based agent authorisation" in {
      ResourceToAuthorise(HttpVerb("GET"), Regime("agent"), AccountId("testid")).buildUrl(authBaseUrl, AuthRequestParameters(ConfidenceLevel, agentRoleRequired = Some("admin"), delegatedAuthRule = Some("lp-paye"))) shouldBe s"authBase/authorise/read/agent/testid?confidenceLevel=$ConfidenceLevel&agentRoleRequired=admin&delegatedAuthRule=lp-paye"
    }
  }


  private trait SetupForAuthorisation {
    val resourceToAuthorise = ResourceToAuthorise(HttpVerb("GET"), Regime("foo"), AccountId("testid"))

    val authResponse : WSResponse = mock[WSResponse]
    val authConnector = new AuthConnector {

      override def authBaseUrl = "authBase"

      override protected def callAuth(url: String)(implicit hc: HeaderCarrier): Future[WSResponse] = {
        Future.successful(authResponse)
      }
    }

  }

  "AuthConnector.authorise" should {
    val authConnector = new AuthConnector {
      var calledUrl: Option[String] = None

      override def authBaseUrl = "authBase"

      override protected def callAuth(url: String)(implicit hc: HeaderCarrier): Future[WSResponse] = {
        calledUrl = Some(url)
        Future.failed(new Exception(""))
      }
    }

    "invoke callAuth with accountId" in {
      val resourceToAuthorise = ResourceToAuthorise(HttpVerb("GET"), Regime("foo"), AccountId("testid"))
      authConnector.authorise(resourceToAuthorise, AuthRequestParameters(ConfidenceLevel))(new HeaderCarrier)
      authConnector.calledUrl shouldBe Some(s"authBase/authorise/read/foo/testid?confidenceLevel=$ConfidenceLevel")
    }

    "invoke callAuth without accountId" in {
      val resourceToAuthorise = ResourceToAuthorise(HttpVerb("GET"), Regime("foo"))
      authConnector.authorise(resourceToAuthorise, AuthRequestParameters(ConfidenceLevel))(new HeaderCarrier)
      authConnector.calledUrl shouldBe Some(s"authBase/authorise/read/foo?confidenceLevel=$ConfidenceLevel")
    }

    "return auth result with the headers" in new SetupForAuthorisation {
      when(authResponse.status).thenReturn(200)
      when(authResponse.allHeaders).thenReturn(Map("a-header" -> Seq("a-value")))
      val result = authConnector.authorise(resourceToAuthorise, AuthRequestParameters(ConfidenceLevel))(new HeaderCarrier)
      status(result) shouldBe 200
      result.futureValue.header.headers("a-header") shouldBe "a-value"
    }
  }

}
