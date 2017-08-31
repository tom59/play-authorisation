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

package uk.gov.hmrc.play.auth.microservice.connectors

import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpecLike}
import play.api.libs.ws.WSResponse
import play.api.test.Helpers._
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class AuthConnectorSpec extends WordSpecLike with Matchers with MockitoSugar with ScalaFutures {
  val stubAuthConnector = new AuthConnector {
    override def authBaseUrl = ???

    override def GET[A](url: String)(implicit rds: HttpReads[A], hc: HeaderCarrier, ec: ExecutionContext): Future[A] = ???
    override def GET[A](url: String, queryParams: Seq[(String, String)])(implicit rds: HttpReads[A], hc: HeaderCarrier, ec: ExecutionContext): Future[A] = ???
  }


  val confidenceLevel = ConfidenceLevel.L100
  "ResourceToAuthorise.buildUrl" should {

    val authBaseUrl = "authBase"
    "generate a url for get authorisation" in {
      RegimeAndIdResourceToAuthorise(HttpVerb("GET"), Regime("foo"), AccountId("testid")).buildUrl(authBaseUrl, AuthRequestParameters(confidenceLevel)) shouldBe s"authBase/authorise/read/foo/testid?confidenceLevel=$confidenceLevel"
    }
    "generate a url for head authorisation" in {
      RegimeAndIdResourceToAuthorise(HttpVerb("HEAD"), Regime("foo"), AccountId("testid")).buildUrl(authBaseUrl, AuthRequestParameters(confidenceLevel)) shouldBe s"authBase/authorise/read/foo/testid?confidenceLevel=$confidenceLevel"
    }
    "generate a url for post authorisation" in {
      RegimeAndIdResourceToAuthorise(HttpVerb("POST"), Regime("foo"), AccountId("testid")).buildUrl(authBaseUrl, AuthRequestParameters(confidenceLevel)) shouldBe s"authBase/authorise/write/foo/testid?confidenceLevel=$confidenceLevel"
    }
    "generate a url for put authorisation" in {
      RegimeAndIdResourceToAuthorise(HttpVerb("PUT"), Regime("foo"), AccountId("testid")).buildUrl(authBaseUrl, AuthRequestParameters(confidenceLevel)) shouldBe s"authBase/authorise/write/foo/testid?confidenceLevel=$confidenceLevel"
    }
    "generate a url for delete authorisation" in {
      RegimeAndIdResourceToAuthorise(HttpVerb("DELETE"), Regime("foo"), AccountId("testid")).buildUrl(authBaseUrl, AuthRequestParameters(confidenceLevel)) shouldBe s"authBase/authorise/write/foo/testid?confidenceLevel=$confidenceLevel"
    }

    "generate a url for role based agent authorisation" in {
      RegimeAndIdResourceToAuthorise(HttpVerb("GET"), Regime("agent"), AccountId("testid")).buildUrl(authBaseUrl, AuthRequestParameters(confidenceLevel, agentRoleRequired = Some("admin"))) shouldBe s"authBase/authorise/read/agent/testid?confidenceLevel=$confidenceLevel&agentRoleRequired=admin"
    }

    "generate a url for rule based agent authorisation" in {
      RegimeAndIdResourceToAuthorise(HttpVerb("GET"), Regime("agent"), AccountId("testid")).buildUrl(authBaseUrl, AuthRequestParameters(confidenceLevel, delegatedAuthRule = Some("lp-paye"))) shouldBe s"authBase/authorise/read/agent/testid?confidenceLevel=$confidenceLevel&delegatedAuthRule=lp-paye"
    }

    "generate a url for role and rule based agent authorisation" in {
      RegimeAndIdResourceToAuthorise(HttpVerb("GET"), Regime("agent"), AccountId("testid")).buildUrl(authBaseUrl, AuthRequestParameters(confidenceLevel, agentRoleRequired = Some("admin"), delegatedAuthRule = Some("lp-paye"))) shouldBe s"authBase/authorise/read/agent/testid?confidenceLevel=$confidenceLevel&agentRoleRequired=admin&delegatedAuthRule=lp-paye"
    }

    "generate a url for role , rule based agent authorisation and privileged access" in {
      RegimeAndIdResourceToAuthorise(HttpVerb("GET"), Regime("agent"), AccountId("testid")).
        buildUrl(authBaseUrl, AuthRequestParameters(confidenceLevel, agentRoleRequired = Some("admin"), delegatedAuthRule = Some("lp-paye"), privilegedAccess = Some("foo"))) shouldBe s"authBase/authorise/read/agent/testid?confidenceLevel=$confidenceLevel&agentRoleRequired=admin&delegatedAuthRule=lp-paye&privilegedAccess=foo"
    }
    "generate a url for privileged access " in {
      RegimeAndIdResourceToAuthorise(HttpVerb("GET"), Regime("agent"), AccountId("testid")).buildUrl(authBaseUrl, AuthRequestParameters(confidenceLevel, privilegedAccess = Some("foo"))) shouldBe s"authBase/authorise/read/agent/testid?confidenceLevel=$confidenceLevel&privilegedAccess=foo"
    }
  }


  private trait SetupForAuthorisation {
    val resourceToAuthorise = RegimeAndIdResourceToAuthorise(HttpVerb("GET"), Regime("foo"), AccountId("testid"))

    val authResponse : HttpResponse = mock[HttpResponse]
    val authConnector = new AuthConnector {

      override def authBaseUrl = "authBase"

      override def GET[A](url: String)(implicit rds: HttpReads[A], hc: HeaderCarrier, ec: ExecutionContext): Future[A] = {
        Future.successful(authResponse.asInstanceOf[A])
      }

      override def GET[A](url: String, queryParams: Seq[(String, String)])(implicit rds: HttpReads[A], hc: HeaderCarrier, ec: ExecutionContext): Future[A] = ???
    }

  }

  "AuthConnector.authorise" should {
    implicit def hc = HeaderCarrier()

    val authConnector = new AuthConnector {
      var calledUrl: Option[String] = None

      override def authBaseUrl = "authBase"

      override def GET[A](url: String)(implicit rds: HttpReads[A], hc: HeaderCarrier, ec: ExecutionContext): Future[A] = {
        calledUrl = Some(url)
        Future.failed(new Exception(""))
      }

      override def GET[A](url: String, queryParams: Seq[(String, String)])(implicit rds: HttpReads[A], hc: HeaderCarrier, ec: ExecutionContext): Future[A] = ???
    }

    "invoke callAuth with accountId" in {
      val resourceToAuthorise = RegimeAndIdResourceToAuthorise(HttpVerb("GET"), Regime("foo"), AccountId("testid"))
      authConnector.authorise(resourceToAuthorise, AuthRequestParameters(confidenceLevel))
      authConnector.calledUrl shouldBe Some(s"authBase/authorise/read/foo/testid?confidenceLevel=$confidenceLevel")
    }

    "invoke callAuth with accountId and privileged access" in {
      val resourceToAuthorise = RegimeAndIdResourceToAuthorise(HttpVerb("GET"), Regime("foo"), AccountId("testid"))
      authConnector.authorise(resourceToAuthorise, AuthRequestParameters(confidenceLevel, privilegedAccess = Some("foo")))
      authConnector.calledUrl shouldBe Some(s"authBase/authorise/read/foo/testid?confidenceLevel=$confidenceLevel&privilegedAccess=foo")
    }

    "invoke callAuth without accountId" in {
      val resourceToAuthorise = RegimeResourceToAuthorise(HttpVerb("GET"), Regime("foo"))
      authConnector.authorise(resourceToAuthorise, AuthRequestParameters(confidenceLevel))
      authConnector.calledUrl shouldBe Some(s"authBase/authorise/read/foo?confidenceLevel=$confidenceLevel")
    }

    "return auth result with the headers" in new SetupForAuthorisation {
      when(authResponse.status).thenReturn(200)
      when(authResponse.allHeaders).thenReturn(Map("a-header" -> Seq("a-value")))
      val result = authConnector.authorise(resourceToAuthorise, AuthRequestParameters(confidenceLevel))
      status(result) shouldBe 200
      result.futureValue.header.headers("a-header") shouldBe "a-value"
    }
  }

}
