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

package uk.gov.hmrc.play.auth.microservice

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{WordSpecLike, Matchers => MatchersResults}
import play.api.mvc.{Result, Results}
import play.api.test.Helpers._
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads}
import uk.gov.hmrc.play.auth.microservice.connectors.{AuthConnector, AuthRequestParameters, ConfidenceLevel, ResourceToAuthorise}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class AuthorisationChecksSpec extends WordSpecLike with MatchersResults with ScalaFutures  {

  trait Setup extends AuthorisationChecks {

    def connectorResult: Result = Results.Ok

    class TestAuthConnector extends AuthConnector {
      var capture: Option[String] = None

      override def authBaseUrl = "authBaseUrl"

      override def authorise(resource: ResourceToAuthorise, authRequestParameters: AuthRequestParameters)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result] = {
        this.capture = Some(resource.buildUrl(authBaseUrl, authRequestParameters))
        Future.successful(connectorResult)
      }

      override def GET[A](url: String)(implicit rds: HttpReads[A], hc: HeaderCarrier, ec: ExecutionContext): Future[A] = ???
      override def GET[A](url: String, queryParams: Seq[(String, String)])(implicit rds: HttpReads[A], hc: HeaderCarrier, ec: ExecutionContext): Future[A] = ???
    }

    val testAuthConnector = new TestAuthConnector
    def authConnector: AuthConnector = testAuthConnector
  }

  "AuthorisationChecks.isAuthorisedFor" should {

    "invoke auth authorise end-point for enrolments" in new Setup  {

      implicit val headerCarrier = HeaderCarrier()
      val response = isAuthorisedFor("foo")
      status(response) shouldBe 200
      testAuthConnector.capture shouldBe Some("authBaseUrl/authorise/enrolment/foo?confidenceLevel=50")

    }

    "invoke auth authorise end-point for enrolments with the passed in CL" in new Setup  {

      implicit val headerCarrier = HeaderCarrier()
      val response = isAuthorisedFor("fifa", ConfidenceLevel.L200)
      status(response) shouldBe 200
      testAuthConnector.capture shouldBe Some("authBaseUrl/authorise/enrolment/fifa?confidenceLevel=200")

    }
  }
}
