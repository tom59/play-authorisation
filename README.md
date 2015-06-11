play-authorisation
====
[![Build Status](https://travis-ci.org/hmrc/play-authorisation.svg?branch=master)](https://travis-ci.org/hmrc/play-authorisation) [ ![Download](https://api.bintray.com/packages/hmrc/releases/play-authorisation/images/download.svg) ](https://bintray.com/hmrc/releases/play-authorisation/_latestVersion)

A library used for communicating with the auth service to authorise requests
from microservices


### Assurance Level Configuration
Level of assurance for micro services can be configured in two ways. One option is to configure at the controllers level so that it's applied to all controllers of the micro service
The other option is fine configuring at each controller level. When both global level and controller level is defined, the individual controller level assurance takes precedence

By default all the micro service will have assurance level of 2 if nothing is configured.

## Global Level Configuration
Add `defaultLevelOfAssurance` to `controllers` section of your application.conf

Example
```json
{
    controllers {
      defaultLevelOfAssurance = "1"
    }
}
```

## Controller Level Configuration
Add `levelOfAssurance` to `controllers` section of your application.conf

Example
```json
{
    controllers {
      YourController{
        needsAuth = true
        authParams {
          levelOfAssurance = "1"
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



