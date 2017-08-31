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

import play.api.http.HttpEntity
import play.api.libs.iteratee.Enumerator
import play.api.libs.json._
import play.api.mvc.{ResponseHeader, Result}
import uk.gov.hmrc.http.{CoreGet, HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

case class HttpVerb(method: String) extends AnyVal

trait ResourceToAuthorise {

  def buildUrl(authBaseUrl: String, authRequestParameters: AuthRequestParameters): String

  protected def action(method: HttpVerb) =
    method.method.toUpperCase match {
      case "GET" | "HEAD" => "read"
      case _ => "write"
    }
}

case class RegimeAndIdResourceToAuthorise(method: HttpVerb, regime: Regime, accountId: AccountId) extends ResourceToAuthorise {
  def buildUrl(authBaseUrl: String, authRequestParameters: AuthRequestParameters): String = {
      s"$authBaseUrl/authorise/${action(method)}/$regime/$accountId${authRequestParameters.asQueryParams}"
  }
}

case class RegimeResourceToAuthorise(method: HttpVerb, regime: Regime) extends ResourceToAuthorise {
  def buildUrl(authBaseUrl: String, authRequestParameters: AuthRequestParameters): String = {
    s"$authBaseUrl/authorise/${action(method)}/$regime${authRequestParameters.asQueryParams}"
  }
}

case class EnrolmentToAuthorise(permission: String) extends ResourceToAuthorise {
  def buildUrl(authBaseUrl: String, authRequestParameters: AuthRequestParameters): String = {
    s"$authBaseUrl/authorise/enrolment/$permission${authRequestParameters.asQueryParams}"
  }
}

case class Regime(value: String) extends AnyVal {
  override def toString: String = value
}

case class AccountId(value: String) extends AnyVal {
  override def toString: String = value
}


sealed abstract class ConfidenceLevel(val level: Int) extends Ordered[ConfidenceLevel] {
  def compare(that: ConfidenceLevel) = this.level.compare(that.level)
  override val toString = level.toString
}

object ConfidenceLevel {
  case object L500 extends ConfidenceLevel(500)
  case object L300 extends ConfidenceLevel(300)
  case object L200 extends ConfidenceLevel(200)
  case object L100 extends ConfidenceLevel(100)
  case object L50 extends ConfidenceLevel(50)
  case object L0 extends ConfidenceLevel(0)

  val all = Set(L0, L50, L100, L200, L300, L500)

  def fromInt(level: Int): ConfidenceLevel = level match {
    case 500 => L500
    case 300 => L300
    case 200 => L200
    case 100 => L100
    case 50  => L50
    case 0   => L0
    case _   => throw new NoSuchElementException(s"Illegal confidence level: $level")
  }

  implicit val format: Format[ConfidenceLevel] = {
    val reads = Reads[ConfidenceLevel] { json =>
      Try { fromInt(json.as[Int]) } match {
        case Success(level) => JsSuccess(level)
        case Failure(ex) => JsError(ex.getMessage)
      }
    }
    val writes = Writes[ConfidenceLevel] { level => JsNumber(level.level) }
    Format(reads, writes)
  }
}

class Requirement[T](maybe: Option[T]) {
  def ifPresent(test: T => Boolean): Boolean = maybe.fold(true)(test)
}


case class AuthRequestParameters(confidenceLevel: ConfidenceLevel, agentRoleRequired: Option[String] = None, delegatedAuthRule: Option[String] = None, privilegedAccess: Option[String] = None) {
  implicit def optionRequirement[T](maybe: Option[T]): Requirement[T] = new Requirement(maybe)
  require (agentRoleRequired.ifPresent(_.nonEmpty), "agentRoleRequired should not be empty")
  require (delegatedAuthRule.ifPresent(_.nonEmpty), "delegatedAuthRule should not be empty")

  def asQueryParams = {
    val params = Seq(s"confidenceLevel=$confidenceLevel")++agentRoleRequired.map(v => s"agentRoleRequired=$v")++delegatedAuthRule.map(v => s"delegatedAuthRule=$v") ++privilegedAccess.map(v => s"privilegedAccess=$v")
    params.mkString("?", "&", "")
  }
}

trait AuthConnector extends CoreGet {
  def authBaseUrl: String

  def authorise(resource: ResourceToAuthorise, authRequestParameters: AuthRequestParameters)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result] = {
    val url = resource.buildUrl(authBaseUrl, authRequestParameters)
    GET[HttpResponse](url) map { response =>
      val headers = response.allHeaders map {
        h => (h._1, h._2.head)
      }
      Result(ResponseHeader(response.status, headers), HttpEntity.NoEntity)
    }
  }
}
