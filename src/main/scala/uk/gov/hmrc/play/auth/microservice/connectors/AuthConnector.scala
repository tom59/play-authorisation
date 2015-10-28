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

import play.api.libs.iteratee.Enumerator
import play.api.libs.ws.WSResponse
import play.api.mvc.{ResponseHeader, Result}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.connectors.Connector
import uk.gov.hmrc.play.http.logging.ConnectionTracing

import scala.concurrent.Future

case class HttpVerb(method: String) extends AnyVal

case class ResourceToAuthorise(method: HttpVerb, regime: Regime, accountId: Option[AccountId]) {

  def buildUrl(authBaseUrl: String, authRequestParameters: AuthRequestParameters): String = {
    accountId.fold(s"$authBaseUrl/authorise/${action(method)}/$regime${authRequestParameters.asQueryParams}"){
      accId => s"$authBaseUrl/authorise/${action(method)}/$regime/$accId${authRequestParameters.asQueryParams}"
    }
  }

  private def action(method: HttpVerb) =
    method.method.toUpperCase match {
      case "GET" | "HEAD" => "read"
      case _ => "write"
    }

}

object ResourceToAuthorise {

  def apply(method: HttpVerb, regime: Regime): ResourceToAuthorise = {
    ResourceToAuthorise(method, regime, None)
  }

  def apply(method: HttpVerb, regime: Regime, accountId: AccountId): ResourceToAuthorise = {
    ResourceToAuthorise(method, regime, Some(accountId))
  }
}

case class Regime(value: String) extends AnyVal {
  override def toString: String = value
}

case class AccountId(value: String) extends AnyVal {
  override def toString: String = value
}

class Requirement[T](maybe: Option[T]) {
  def ifPresent(test: T => Boolean): Boolean = maybe.fold(true)(test)
}


case class AuthRequestParameters(confidenceLevel: Int, agentRoleRequired: Option[String] = None, delegatedAuthRule: Option[String] = None) {
  implicit def optionRequirement[T](maybe: Option[T]): Requirement[T] = new Requirement(maybe)
  require (agentRoleRequired.ifPresent(_.nonEmpty), "agentRoleRequired should not be empty")
  require (delegatedAuthRule.ifPresent(_.nonEmpty), "delegatedAuthRule should not be empty")

  def asQueryParams = {
    val params = Seq(s"confidenceLevel=$confidenceLevel")++agentRoleRequired.map(v => s"agentRoleRequired=$v")++delegatedAuthRule.map(v => s"delegatedAuthRule=$v")
    params.mkString("?", "&", "")
  }
}

trait AuthConnector extends Connector with ConnectionTracing {
  import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

  def authBaseUrl: String

  def authorise(resource: ResourceToAuthorise, authRequestParameters: AuthRequestParameters)(implicit hc: HeaderCarrier): Future[Result] = {
    val url = resource.buildUrl(authBaseUrl, authRequestParameters)
    callAuth(url) map { response =>
      val headers = response.allHeaders map {
        h => (h._1, h._2.head)
      }
      Result(ResponseHeader(response.status, headers), Enumerator(Array()))
    }
  }

  protected def callAuth(url: String)(implicit hc: HeaderCarrier): Future[WSResponse] = withTracing("GET", url) {
    buildRequest(url).get()
  }
}

