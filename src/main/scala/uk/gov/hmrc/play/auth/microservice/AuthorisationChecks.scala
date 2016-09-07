/*
 * Copyright 2016 HM Revenue & Customs
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

import play.api.mvc.Results
import uk.gov.hmrc.play.auth.microservice.connectors.{AuthConnector, AuthRequestParameters, ConfidenceLevel, EnrolmentToAuthorise}
import uk.gov.hmrc.play.http.HeaderCarrier
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future


trait AuthorisationChecks {

  def authConnector: AuthConnector


  def isAuthorisedFor(enrolment: String, confidenceLevel: ConfidenceLevel = ConfidenceLevel.L50)(implicit headerCarrier: HeaderCarrier) = {
       authConnector.authorise(EnrolmentToAuthorise(enrolment),AuthRequestParameters(confidenceLevel,None,None))
  }

}
