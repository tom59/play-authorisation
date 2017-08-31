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

package uk.gov.hmrc.play.auth.microservice.filters

import play.Routes
import play.api.mvc._
import uk.gov.hmrc.play.auth.controllers
import uk.gov.hmrc.http.{HeaderCarrier, HeaderNames}
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.auth.controllers.{AuthConfig, AuthParamsControllerConfig}
import uk.gov.hmrc.play.auth.microservice.connectors._

import scala.concurrent.Future
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

trait AuthorisationFilter extends Filter {

  def authConnector: AuthConnector
  def controllerNeedsAuth(controllerName: String): Boolean
  def authParamsConfig: AuthParamsControllerConfig


  /**
   * @return None if authorisation is not required OR the RequestHeader does not give enough information for us
   *         to tell if we need authorisation or not
   */
  def authConfig(rh: RequestHeader): Option[AuthConfig] =
    rh.tags.get(Routes.ROUTE_CONTROLLER).flatMap { name =>
      if (controllerNeedsAuth(name)) Some(authParamsConfig.authConfig(name))
      else None
    }

  def apply(next: (RequestHeader) => Future[Result])(rh: RequestHeader): Future[Result] = {
    implicit val hc = HeaderCarrierConverter.fromHeadersAndSession(rh.headers)

    authConfig(rh) match {
      case Some(authConfig) => isAuthorised(rh, authConfig).flatMap { result =>
        result.header.status match {
          case 200 => next(appendSurrogateHeader(rh, result))
          case 401 | 403 => Future.successful(result)
          case _ => Future.successful(Results.Unauthorized)
        }
      }
      case _ => next(rh)
    }
  }

  private def appendSurrogateHeader(rh: RequestHeader, response : Result ): RequestHeader = {
    response.header.headers.get(HeaderNames.surrogate).fold(rh) {
      case "true" => rh.copy(headers = rh.headers.add(HeaderNames.surrogate -> "true"))
      case _ => rh
    }
  }

  private implicit def sequence[T](of: Option[Future[T]])(implicit hc: HeaderCarrier): Future[Option[T]] =
    of.map(f => f.map(Option(_))).getOrElse(Future.successful(None))

  private def isAuthorised(rh: RequestHeader, authConfig: AuthConfig)(implicit hc: HeaderCarrier): Future[Result] = {
    val result: Future[Option[Result]] =
      for {
        verb <- rh.tags.get(Routes.ROUTE_VERB).map(HttpVerb)
        resource <- extractResource(rh, verb, authConfig)

      } yield authConnector.authorise(resource, AuthRequestParameters(authConfig.confidenceLevel,authConfig.agentRole, authConfig.delegatedAuthRule, authConfig.privilegedAccess))

    result.map(_.getOrElse(Results.Unauthorized))
  }

  def extractResource(rh: RequestHeader, verb: HttpVerb, authConfig: AuthConfig): Option[ResourceToAuthorise] =
    authConfig.mode match {
      case "identity" => extractIdentityResourceFromPath(rh.path, verb, authConfig)
      case "identityByRequestParam" => extractIdentityResourceFromQueryParameters(rh.queryString, verb, authConfig)
      case "passcode" => extractPasscodeResource(rh.path, verb, authConfig)
      case "enrolment" => extractEnrolmentResource(rh.path, verb, authConfig)
    }

  private def extractIdentityResourceFromQueryParameters(queryParameters: Map[String, Seq[String]], verb: HttpVerb, authConfig: AuthConfig): Option[ResourceToAuthorise] = {
    val authConfigRegime: Option[controllers.Regime] = authConfig.account flatMap (account => controllers.Regime.allRegimes.find(_.accountName == account))

    for {
      regime <- authConfigRegime
      identifierValues <- queryParameters.get(regime.identifier)
      resource <- identifierValues match {
        case identifierValue :: Nil => Some(RegimeAndIdResourceToAuthorise(verb, Regime(authConfig.servicePrefix + regime.accountName), AccountId(identifierValue)))
        case _ => None
      }
    } yield resource
  }

  private def extractIdentityResourceFromPath(pathString: String, verb: HttpVerb, authConfig: AuthConfig): Option[ResourceToAuthorise] = {

    pathString match {
      case authConfig.pattern(urlAccount, id) =>
        val reconciledAccount = authConfig.account.getOrElse(urlAccount)
        Some(RegimeAndIdResourceToAuthorise(verb, Regime(authConfig.servicePrefix + reconciledAccount), AccountId(id)))
      case _ => None
    }
  }

  private def extractPasscodeResource(pathString: String, verb: HttpVerb, authConfig: AuthConfig): Option[ResourceToAuthorise] = {

    pathString match {
      case authConfig.anonymousLoginPattern(urlAccount) =>
        val reconciledAccount = authConfig.account.getOrElse(urlAccount)
        Some(RegimeResourceToAuthorise(verb, Regime(authConfig.servicePrefix + reconciledAccount)))
      case _ => None
    }
  }

  private def extractEnrolmentResource(pathString: String, verb: HttpVerb, authConfig: AuthConfig): Option[ResourceToAuthorise] = {
    authConfig.enrolment.map(EnrolmentToAuthorise)
  }
}
