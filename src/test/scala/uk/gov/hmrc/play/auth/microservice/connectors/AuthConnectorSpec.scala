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

import org.scalatest.{Matchers, WordSpecLike}
import play.api.libs.json.JsValue
import play.api.libs.ws.{WSCookie, WSResponse}
import uk.gov.hmrc.play.audit.http.HeaderCarrier

import scala.concurrent.Future
import scala.xml.Elem

class AuthConnectorSpec extends WordSpecLike with Matchers {
  val stubAuthConnector = new AuthConnector {
    override def authBaseUrl = ???

    override protected def callAuth(url: String)(implicit hc: HeaderCarrier): Future[WSResponse] = ???
  }

  "AuthConnector.isSurrogate" should {

    case class StubWSResponse(headerValToReturn: Option[String]) extends WSResponse {
      override def allHeaders: Map[String, Seq[String]] = ???
      override def statusText: String = ???
      override def underlying[T]: T = ???
      override def xml: Elem = ???
      override def body: String = ???
      override def header(key: String): Option[String] = headerValToReturn
      override def cookie(name: String): Option[WSCookie] = ???
      override def cookies: Seq[WSCookie] = ???
      override def status: Int = ???
      override def json: JsValue = ???
    }

    "return true if the surrogate header contains the string \"true\"" in {
      stubAuthConnector.isSurrogate(StubWSResponse(Some("true"))) shouldBe true
    }

    "return false if the surrogate header does not exist" in {
      stubAuthConnector.isSurrogate(StubWSResponse(None)) shouldBe false
    }

    "return true if the surrogate header contains the string \"false\"" in {
      stubAuthConnector.isSurrogate(StubWSResponse(Some("false"))) shouldBe false
    }

    "return false if the surrogate header is empty" in {
      stubAuthConnector.isSurrogate(StubWSResponse(Some(""))) shouldBe false
    }

    "return false if the surrogate header cannot be mapped to a boolean" in {
      stubAuthConnector.isSurrogate(StubWSResponse(Some("invalid"))) shouldBe false
    }
  }


  val loa: String = "1.5"
  "ResourceToAuthorise.buildUrl" should {

    val authBaseUrl = "authBase"
    "generate a url for get authorisation" in {
      ResourceToAuthorise(HttpVerb("GET"), Regime("foo"), AccountId("testid")).buildUrl(authBaseUrl, AuthRequestParameters(loa)) shouldBe s"authBase/authorise/read/foo/testid?levelOfAssurance=$loa"
    }
    "generate a url for head authorisation" in {
      ResourceToAuthorise(HttpVerb("HEAD"), Regime("foo"), AccountId("testid")).buildUrl(authBaseUrl, AuthRequestParameters(loa)) shouldBe s"authBase/authorise/read/foo/testid?levelOfAssurance=$loa"
    }
    "generate a url for post authorisation" in {
      ResourceToAuthorise(HttpVerb("POST"), Regime("foo"), AccountId("testid")).buildUrl(authBaseUrl, AuthRequestParameters(loa)) shouldBe s"authBase/authorise/write/foo/testid?levelOfAssurance=$loa"
    }
    "generate a url for put authorisation" in {
      ResourceToAuthorise(HttpVerb("PUT"), Regime("foo"), AccountId("testid")).buildUrl(authBaseUrl, AuthRequestParameters(loa)) shouldBe s"authBase/authorise/write/foo/testid?levelOfAssurance=$loa"
    }
    "generate a url for delete authorisation" in {
      ResourceToAuthorise(HttpVerb("DELETE"), Regime("foo"), AccountId("testid")).buildUrl(authBaseUrl, AuthRequestParameters(loa)) shouldBe s"authBase/authorise/write/foo/testid?levelOfAssurance=$loa"
    }

    "generate a url for role based agent authorisation" in {
      ResourceToAuthorise(HttpVerb("GET"), Regime("agent"), AccountId("testid")).buildUrl(authBaseUrl, AuthRequestParameters(loa, agentRoleRequired = Some("admin"))) shouldBe s"authBase/authorise/read/agent/testid?levelOfAssurance=$loa&agentRoleRequired=admin"
    }

    "generate a url for rule based agent authorisation" in {
      ResourceToAuthorise(HttpVerb("GET"), Regime("agent"), AccountId("testid")).buildUrl(authBaseUrl, AuthRequestParameters(loa, delegatedAuthRule = Some("lp-paye"))) shouldBe s"authBase/authorise/read/agent/testid?levelOfAssurance=$loa&delegatedAuthRule=lp-paye"
    }

    "generate a url for role and rule based agent authorisation" in {
      ResourceToAuthorise(HttpVerb("GET"), Regime("agent"), AccountId("testid")).buildUrl(authBaseUrl, AuthRequestParameters(loa, agentRoleRequired = Some("admin"), delegatedAuthRule = Some("lp-paye"))) shouldBe s"authBase/authorise/read/agent/testid?levelOfAssurance=$loa&agentRoleRequired=admin&delegatedAuthRule=lp-paye"
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
      authConnector.authorise(resourceToAuthorise, AuthRequestParameters(loa))(new HeaderCarrier)
      authConnector.calledUrl shouldBe Some(s"authBase/authorise/read/foo/testid?levelOfAssurance=$loa")
    }

    "invoke callAuth without accountId" in {
      val resourceToAuthorise = ResourceToAuthorise(HttpVerb("GET"), Regime("foo"))
      authConnector.authorise(resourceToAuthorise, AuthRequestParameters(loa))(new HeaderCarrier)
      authConnector.calledUrl shouldBe Some(s"authBase/authorise/read/foo?levelOfAssurance=$loa")
    }
  }

}
