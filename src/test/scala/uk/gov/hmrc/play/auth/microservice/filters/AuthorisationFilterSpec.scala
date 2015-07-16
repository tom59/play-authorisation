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

import org.mockito.Mockito.{verify, when}
import org.mockito.{ArgumentCaptor, Matchers}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers => MatchersResults, WordSpecLike}
import play.api.Routes
import play.api.mvc.Results._
import play.api.mvc.{AnyContentAsEmpty, RequestHeader, Result, Results}
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.play.auth.controllers.{AuthConfig, LevelOfAssurance}
import uk.gov.hmrc.play.auth.microservice.connectors.{AuthConnector, AuthRequestParameters}
import uk.gov.hmrc.play.http.HeaderNames

import scala.concurrent.Future

class AuthorisationFilterSpec extends WordSpecLike with MatchersResults with MockitoSugar with ScalaFutures {


  private trait Setup {

    val levelOfAssurance = LevelOfAssurance.LOA_1_5
    val configWithLoa = AuthConfig(levelOfAssurance = levelOfAssurance)
    val authConnectorMock = mock[AuthConnector]
  
    val filter = new AuthorisationFilter {
      override def authConnector: AuthConnector = authConnectorMock
  
      override def authConfig(rh: RequestHeader): Option[AuthConfig] = Some(configWithLoa)
    }
    
    def filterRequest(connectorResult: Future[Result], isSurrogate: Boolean= false): Future[Result] = {
      when(authConnectorMock.authorise(Matchers.any(), Matchers.any())(Matchers.any())).thenReturn(connectorResult)
      val req = FakeRequest("GET", "/myregime/myId", FakeHeaders(), AnyContentAsEmpty, tags = Map(Routes.ROUTE_VERB-> "GET"))

      filter((next: RequestHeader) => {
        val isSurrogate = next.headers.get(HeaderNames.surrogate) == Some("true")
        Future.successful(Ok(if (isSurrogate) "All is surrogate" else "All is good"))
      })(req)
    }
  }

  "AuthorisationFilter" should {

    "add the levelOfAssurance when calling auth" in new Setup {
      val result = filterRequest(Future.successful(Results.Ok))
      status(result) shouldBe 200
      contentAsString(result) shouldBe "All is good"

      val captor = ArgumentCaptor.forClass(classOf[AuthRequestParameters])
      verify(authConnectorMock).authorise(Matchers.any(),captor.capture())(Matchers.any())
      captor.getValue().levelOfAssurance shouldBe levelOfAssurance.toString
    }

    "return 401 if auth returns 401" in new Setup {
      val result = filterRequest(Future.successful(Results.Unauthorized))
      status(result) shouldBe 401
    }

    "keep all headers if auth returns 401" in new Setup {
      val result = filterRequest(Future.successful(Results.Unauthorized.withHeaders("WWW-Authenticated" -> "xxx")))
      status(result) shouldBe 401
      header("WWW-Authenticated", result) shouldBe Some("xxx")
    }

    "return 403 if auth returns 403" in new Setup {
      val result = filterRequest(Future.successful(Forbidden))
      status(result) shouldBe 403
    }

    "return 401 if auth returns any other error" in new Setup {
      val result = filterRequest(Future.successful(Results.InternalServerError))
      status(result) shouldBe 401
    }

    "let the request though if auth returns 200" in new Setup {
      val result = filterRequest(Future.successful(Results.Ok))
      status(result) shouldBe 200
      contentAsString(result) shouldBe "All is good"
    }

    "add surrogate header to request if auth responds with that header set to true" in new Setup {
      val result = filterRequest(Future.successful(Results.Ok.withHeaders(HeaderNames.surrogate -> "true")))
      status(result) shouldBe 200
      contentAsString(result) shouldBe "All is surrogate"
    }

    "not add surrogate header to request if auth responds with that header set to any other value" in new Setup {
      val result = filterRequest(Future.successful(Results.Ok.withHeaders(HeaderNames.surrogate -> "foo")))
      status(result) shouldBe 200
      contentAsString(result) shouldBe "All is good"
    }
  }
}
