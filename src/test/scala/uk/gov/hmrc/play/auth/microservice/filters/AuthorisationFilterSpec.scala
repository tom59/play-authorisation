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

package uk.gov.hmrc.play.auth.microservice.filters

import _root_.play.api.Routes._
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers => MatchersResults, WordSpecLike}
import play.api.mvc.{AnyContentAsEmpty, RequestHeader, Result, Results}
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.auth.controllers.{AuthConfig, AuthParamsControllerConfig}
import uk.gov.hmrc.play.auth.microservice.connectors.{AuthConnector, AuthRequestParameters, _}
import uk.gov.hmrc.play.http.HeaderNames

import scala.collection.JavaConversions._
import scala.concurrent.Future

class AuthorisationFilterSpec extends WordSpecLike with MatchersResults with ScalaFutures {

 
  "AuthorisationFilter.extractAccountAndAccountId with default AuthConfig" should {
    val defaultAuthConfig = AuthConfig(confidenceLevel = 500)

    "extract (vat, 99999999) from /vat/99999999" in new SetUp {
      val verb = HttpVerb("GET")
      val resource = ResourceToAuthorise(verb, Regime("vat"), AccountId("99999999"))
      authFilter.extractResource("/vat/99999999", verb, defaultAuthConfig) shouldBe Some(resource)
    }

    "extract (vat, 99999999) from /vat/99999999/calendar" in new SetUp {
      val verb = HttpVerb("GET")
      val resource = ResourceToAuthorise(verb, Regime("vat"), AccountId("99999999"))
      authFilter.extractResource("/vat/99999999/calendar", verb, defaultAuthConfig) shouldBe Some(resource)
    }

    "extract (epaye, 840%2FMODE26A) from /epaye/840%2FMODE26A" in new SetUp {
      val verb = HttpVerb("GET")
      val resource = ResourceToAuthorise(verb, Regime("epaye"), AccountId("840%2FMODE26A"))
      authFilter.extractResource("/epaye/840%2FMODE26A", verb, defaultAuthConfig) shouldBe Some(resource)
    }

    "extract (epaye, 840%2FMODE26A) from /epaye/840%2FMODE26A/account-summary" in new SetUp {
      val verb = HttpVerb("GET")
      val resource = ResourceToAuthorise(verb, Regime("epaye"), AccountId("840%2FMODE26A"))
      authFilter.extractResource("/epaye/840%2FMODE26A/account-summary", verb, defaultAuthConfig) shouldBe Some(resource)
    }

    "extract None from /ping" in new SetUp {
      authFilter.extractResource("ping", HttpVerb("GET"), defaultAuthConfig) shouldBe None
    }
  }

  "AuthorisationFilter.extractResource with AuthConfig configured for government gateway" should {

    "extract (government-gateway-profile/auth/oid, 08732408734) from /profile/auth/oid/08732408734 as special case for government gateway" in new SetUp {
      val verb = HttpVerb("GET")
      val ggAuthConfig = AuthConfig(pattern = "/(profile/auth/oid)/([\\w]+)[/]?".r, servicePrefix = "government-gateway-", confidenceLevel = 500)
      val resource = ResourceToAuthorise(verb, Regime("government-gateway-profile/auth/oid"), AccountId("08732408734"))
      authFilter.extractResource("/profile/auth/oid/08732408734", verb, ggAuthConfig) shouldBe Some(resource)
    }
  }

  "AuthorisationFilter.extractResource with AuthConfig configured for anonymous" should {

    "extract (charities, None) from /charities/auth as special case for charities" in new SetUp {
      val verb = HttpVerb("GET")
      val authConfig = AuthConfig(mode = "passcode", confidenceLevel = 500)
      val resource = ResourceToAuthorise(verb, Regime("charities"))
      authFilter.extractResource("/charities/blah", verb, authConfig) shouldBe Some(resource)
    }
  }

  "The AuthorisationFilter.apply method when called" should {

    "properly override the account name from controller config and pass the correct account, accountId and delegate authorisation data to the auth connector" in new SetUp {

      val request = FakeRequest("GET", "/anaccount/anid/data", FakeHeaders(), "", tags = Map(ROUTE_VERB -> "GET", ROUTE_CONTROLLER -> "DelegateAuthController"))

      val result = authFilterWithAccountName.apply((h: RequestHeader) => Future.successful(new Results.Status(200)))(request).futureValue

      testAuthConnector.capture shouldBe Some(AuthCallCaptured(HttpVerb("GET"), Regime("agent"), Some(AccountId("anid")), AuthRequestParameters(agentRoleRequired = Some("admin"), delegatedAuthRule = Some("lp-paye"), confidenceLevel = ConfidenceLevel)))
    }

    "not override the account name if not specified in the controller config and pass the account from the url and accountId to the auth connector" in new SetUp {

      val request = FakeRequest("GET", "/anaccount/anid/data", FakeHeaders(), "", tags = Map(ROUTE_VERB -> "GET", ROUTE_CONTROLLER -> "DelegateAuthController"))

      val result = authFilter.apply((h: RequestHeader) => Future.successful(new Results.Status(200)))(request).futureValue

      testAuthConnector.capture shouldBe Some(AuthCallCaptured(HttpVerb("GET"), Regime("anaccount"), Some(AccountId("anid")), AuthRequestParameters(agentRoleRequired = Some("admin"), delegatedAuthRule = Some("lp-paye"), confidenceLevel = ConfidenceLevel)))
    }
  }

  "AuthorisationFilter" should {

    "add the confidenceLevel when calling auth" in new SetUp {
      val result = filterRequest
      status(result) shouldBe 200
      contentAsString(result) shouldBe "All is good"

      testAuthConnector.capture.map(_.authRequestParameters.confidenceLevel) shouldBe Some(ConfidenceLevel)
    }

    "return 401 if auth returns 401" in new SetUp(Results.Unauthorized) {
      val result = filterRequest
      status(result) shouldBe 401
    }

    "keep all headers if auth returns 401" in new SetUp(Results.Unauthorized.withHeaders("WWW-Authenticated" -> "xxx")) {
      val result = filterRequest
      status(result) shouldBe 401
      header("WWW-Authenticated", result) shouldBe Some("xxx")
    }

    "return 403 if auth returns 403" in new SetUp(Results.Forbidden) {
      val result = filterRequest
      status(result) shouldBe 403
    }

    "return 401 if auth returns any other error" in new SetUp(Results.InternalServerError) {
      val result = filterRequest
      status(result) shouldBe 401
    }

    "let the request though if auth returns 200" in new SetUp {
      val result = filterRequest
      status(result) shouldBe 200
      contentAsString(result) shouldBe "All is good"
    }

    "add surrogate header to request if auth responds with that header set to true" in new SetUp(Results.Ok.withHeaders(HeaderNames.surrogate -> "true")) {
      val result = filterRequest
      status(result) shouldBe 200
      contentAsString(result) shouldBe "All is surrogate"
    }

    "not add surrogate header to request if auth responds with that header set to any other value" in new SetUp(Results.Ok.withHeaders(HeaderNames.surrogate -> "foo")) {
      val result = filterRequest
      status(result) shouldBe 200
      contentAsString(result) shouldBe "All is good"
    }
  }

  class SetUp(connectorResult: Result = Results.Ok) {

    val ConfidenceLevel = 100

    def filterRequest: Future[Result] = {
      val req = FakeRequest("GET", "/myregime/myId", FakeHeaders(), AnyContentAsEmpty, tags = Map(ROUTE_VERB -> "GET", ROUTE_CONTROLLER -> "DelegateAuthController"))

      authFilter ( (next: RequestHeader) => {
        val isSurrogate = next.headers.get(HeaderNames.surrogate).contains("true")
        Future.successful(Results.Ok(if (isSurrogate) "All is surrogate" else "All is good"))
      })(req)
    }

    case class AuthCallCaptured(method: HttpVerb, account: Regime, accountId: Option[AccountId], authRequestParameters: AuthRequestParameters)

    class TestAuthConnector extends AuthConnector {
      var capture: Option[AuthCallCaptured] = None

      override def authBaseUrl = "authBaseUrl"

      override def authorise(resource: ResourceToAuthorise, authRequestParameters: AuthRequestParameters)(implicit hc: HeaderCarrier): Future[Result] = {
        this.capture = Some(AuthCallCaptured(resource.method, resource.regime, resource.accountId, authRequestParameters))
        Future.successful(connectorResult)
      }
    }

    val testAuthConnector = new TestAuthConnector

    class TestAuthorisationFilter(config: Map[String, String] = Map.empty) extends AuthorisationFilter {
      override def controllerNeedsAuth(controllerName: String) = true

      private val configMap = Map(
        "DelegateAuthController.authParams.agentRole" -> "admin",
        "DelegateAuthController.authParams.delegatedAuthRule" -> "lp-paye",
        "DelegateAuthController.authParams.confidenceLevel" -> ConfidenceLevel.toString
      )

      override val authParamsConfig =
        new AuthParamsControllerConfig {
          override def controllerConfigs: Config = ConfigFactory.parseMap(configMap ++ config)
        }

      override lazy val authConnector = testAuthConnector
    }

    val authFilter = new TestAuthorisationFilter()

    val authFilterWithAccountName = new TestAuthorisationFilter(Map("DelegateAuthController.authParams.account" -> "agent"))
  }
}
