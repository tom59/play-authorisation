play-authorisation
====
[![Build Status](https://travis-ci.org/hmrc/play-authorisation.svg?branch=master)](https://travis-ci.org/hmrc/play-authorisation) [ ![Download](https://api.bintray.com/packages/hmrc/releases/play-authorisation/images/download.svg) ](https://bintray.com/hmrc/releases/play-authorisation/_latestVersion)

A library used for communicating with the auth service to authorise requests
from microservices


### Confidence Level Configuration
Confidence level for micro services can be configured in two ways. One option is to declare it at the controllers level so that it's applied to all controllers of the micro service
The other option is specifying it for each controller. When both global level and controller level are defined, the individual controller's confidence level takes precedence

The valid Confidence Level values are 0, 100, 200, 300, 500

## Global Level Configuration
Add `confidenceLevel` to `controllers` section of your application.conf

Example
```json
{
    controllers {
      confidenceLevel = 300
    }
}
```

## Controller Level Configuration
Add `confidenceLevel` to `controllers` section of your application.conf

Example
```json
{
    controllers {
      YourController{
        needsAuth = true
        authParams {
          confidenceLevel = 100
        }
      }
    }
}
```

## Auth filter with request parameter
The following configuration will validate the nino or sautr for this URL http://myservice.protected.mdtp/?nino=NINO
```json
{
    controllers {
      YourController{
        authParams = {
          mode = "identityByRequestParam"
          account = "paye"
          confidenceLevel=100
      }
    }
  }
}
```

## Installing

Include the following dependency in your SBT build

``` scala
resolvers += Resolver.bintrayRepo("hmrc", "releases")

libraryDependencies += "uk.gov.hmrc" %% "play-authorisation" % "x.x.x"
```

## License ##

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").


