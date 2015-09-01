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
import org.scalatest.{WordSpecLike, Matchers => MatchersResults}
import play.api.Routes
import play.api.mvc.Results._
import play.api.mvc.{AnyContentAsEmpty, RequestHeader}
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.play.auth.controllers.{AuthConfig, AuthParamsControllerConfig, LevelOfAssurance}
import uk.gov.hmrc.play.auth.microservice.connectors._

import scala.concurrent.Future
import scala.concurrent.duration._

class AuthorisationFilterLOASpec extends WordSpecLike with MatchersResults with MockitoSugar with ScalaFutures {


  val levelOfAssurance = LevelOfAssurance.LOA_1_5
  val configWithLoa = AuthConfig(levelOfAssurance = levelOfAssurance)
  val authConnectorMock = mock[AuthConnector]

  val filter = new AuthorisationFilter {
    override def authConnector: AuthConnector = authConnectorMock

    override def authConfig(rh: RequestHeader): Option[AuthConfig] = Some(configWithLoa)

    override def controllerNeedsAuth(controllerName: String): Boolean = true

    override def authParamsConfig: AuthParamsControllerConfig = ???
  }

  "AuthorisationFilter" should {
    "add the levelOfAssurance when calling auth" in {

      import akka.util.Timeout
      implicit val timeout = Timeout(3 seconds)

      when(authConnectorMock.authorise(Matchers.any(), Matchers.any())(Matchers.any())).thenReturn(Future.successful(AuthorisationResult(true, false)))
      val req = FakeRequest("GET", "/myregime/myId", FakeHeaders(), AnyContentAsEmpty, tags = Map(Routes.ROUTE_VERB-> "GET"))

      val result = filter((next: RequestHeader) => Future.successful(Ok("All is good")))(req)
      play.api.test.Helpers.status(result) shouldBe 200
      play.api.test.Helpers.contentAsString(result) shouldBe "All is good"

      val captor = ArgumentCaptor.forClass(classOf[AuthRequestParameters])
      verify(authConnectorMock).authorise(Matchers.any(),captor.capture())(Matchers.any())
      captor.getValue().levelOfAssurance shouldBe levelOfAssurance.toString
    }
  }

}
