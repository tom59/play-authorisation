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
import play.api.mvc.{AnyContentAsEmpty, RequestHeader, Result}
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.play.auth.controllers.{AuthConfig, LevelOfAssurance}
import uk.gov.hmrc.play.auth.microservice.connectors.{Authorised, AuthConnector, AuthRequestParameters, AuthorisationResult}
import scala.concurrent.Future
import scala.concurrent.duration._
import uk.gov.hmrc.play.auth.microservice.connectors.{Authorised, NotAuthenticated, Forbidden}
import uk.gov.hmrc.play.http.{UnauthorizedException, ForbiddenException}


class AuthorisationFilterSpec extends WordSpecLike with MatchersResults with MockitoSugar with ScalaFutures {


  private trait Setup {
    import akka.util.Timeout
    implicit val timeout = Timeout(3 seconds)
      
    val levelOfAssurance = LevelOfAssurance.LOA_1_5
    val configWithLoa = AuthConfig(levelOfAssurance = levelOfAssurance)
    val authConnectorMock = mock[AuthConnector]
  
    val filter = new AuthorisationFilter {
      override def authConnector: AuthConnector = authConnectorMock
  
      override def authConfig(rh: RequestHeader): Option[AuthConfig] = Some(configWithLoa)
    }
    
    def filterRequest(connectorResult: Future[AuthorisationResult]): Future[Result] = {
      when(authConnectorMock.authorise(Matchers.any(), Matchers.any())(Matchers.any())).thenReturn(connectorResult)
      val req = FakeRequest("GET", "/myregime/myId", FakeHeaders(), AnyContentAsEmpty, tags = Map(Routes.ROUTE_VERB-> "GET"))

      filter((next: RequestHeader) => Future.successful(Ok("All is good")))(req)
    }
  }

  "AuthorisationFilter" should {

    "add the levelOfAssurance when calling auth" in new Setup {
      val result = filterRequest(Future.successful(Authorised(false)))
      play.api.test.Helpers.status(result) shouldBe 200
      play.api.test.Helpers.contentAsString(result) shouldBe "All is good"

      val captor = ArgumentCaptor.forClass(classOf[AuthRequestParameters])
      verify(authConnectorMock).authorise(Matchers.any(),captor.capture())(Matchers.any())
      captor.getValue().levelOfAssurance shouldBe levelOfAssurance.toString
    }

    "return 401 if auth returns NotAuthenticated" in new Setup {
      val result = filterRequest(Future.successful(NotAuthenticated()))
      result.failed.futureValue shouldBe a [UnauthorizedException]
    }

    "throw ForbiddenException if auth returns Forbidden" in new Setup {
      val result = filterRequest(Future.successful(Forbidden))
      result.failed.futureValue shouldBe a [ForbiddenException]
    }

    "let the request though if auth returns Authentiated(_)" in new Setup {
      val result = filterRequest(Future.successful(Authorised(false)))
      play.api.test.Helpers.status(result) shouldBe 200
    }
  }
}
