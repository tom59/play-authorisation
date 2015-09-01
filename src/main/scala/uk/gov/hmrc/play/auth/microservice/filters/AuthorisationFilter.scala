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

import play.api.Routes
import play.api.mvc.{Filter, Headers, RequestHeader, Result}
import uk.gov.hmrc.play.audit.http.HeaderCarrier
import uk.gov.hmrc.play.auth.controllers.{AuthParamsControllerConfig, AuthConfig}
import uk.gov.hmrc.play.auth.microservice.connectors._
import uk.gov.hmrc.play.http.logging.LoggingDetails
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.http.{HeaderNames, UnauthorizedException}

import scala.concurrent.Future

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
    implicit val hc = HeaderCarrier.fromHeadersAndSession(rh.headers)

    authConfig(rh) match {
      case Some(authConfig) => isAuthorised(rh, authConfig).flatMap {
        case AuthorisationResult(true, true) => next(appendSurrogateHeader(rh))
        case AuthorisationResult(true, _) => next(rh)
        case _ => throw new UnauthorizedException(s"Authorisation refused for access to ${rh.method} ${rh.uri}")
      }
      case _ => next(rh)
    }
  }

  private def appendSurrogateHeader(rh: RequestHeader): RequestHeader = {
    val existingHeaders = rh.headers.toMap
    val newHeaders = new Headers {
      val data: Seq[(String, Seq[String])] = existingHeaders.toSeq ++ Seq(HeaderNames.surrogate -> Seq("true"))
    }

    rh.copy(headers = newHeaders)
  }

  private implicit def sequence[T](of: Option[Future[T]])(implicit ld: LoggingDetails): Future[Option[T]] =
    of.map(f => f.map(Option(_))).getOrElse(Future.successful(None))

  private def isAuthorised(rh: RequestHeader, authConfig: AuthConfig)(implicit hc: HeaderCarrier): Future[AuthorisationResult] = {
    val result: Future[Option[AuthorisationResult]] =
      for {
        verb <- rh.tags.get(Routes.ROUTE_VERB).map(HttpVerb)
        resource <- extractResource(rh.path, verb, authConfig)

      } yield authConnector.authorise(resource, AuthRequestParameters(authConfig.levelOfAssurance.toString,authConfig.agentRole, authConfig.delegatedAuthRule))

    result.map(_.getOrElse(AuthorisationResult(isAuthorised = false, isSurrogate = false)))
  }

  def extractResource(pathString: String, verb: HttpVerb, authConfig: AuthConfig): Option[ResourceToAuthorise] =
    authConfig.mode match {
      case "identity" => extractIdentityResource(pathString, verb, authConfig)
      case "passcode" => extractPasscodeResource(pathString, verb, authConfig)
    }

  private def extractIdentityResource(pathString: String, verb: HttpVerb, authConfig: AuthConfig): Option[ResourceToAuthorise] = {

    pathString match {
      case authConfig.pattern(urlAccount, id) =>
        val reconciledAccount = authConfig.account.getOrElse(urlAccount)
        Some(ResourceToAuthorise(verb, Regime(authConfig.servicePrefix + reconciledAccount), AccountId(id)))
      case _ => None
    }
  }

  private def extractPasscodeResource(pathString: String, verb: HttpVerb, authConfig: AuthConfig): Option[ResourceToAuthorise] = {

    pathString match {
      case authConfig.anonymousLoginPattern(urlAccount) =>
        val reconciledAccount = authConfig.account.getOrElse(urlAccount)
        Some(ResourceToAuthorise(verb, Regime(authConfig.servicePrefix + reconciledAccount)))
      case _ => None
    }
  }
}
