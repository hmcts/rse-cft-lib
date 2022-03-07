# RSE CFT lib

## Run multiple HMCTS services in a single JVM

* Simple setup - ```./gradlew bootWithCCD```
* Reduced RAM requirements & improved performance
* Improved testability
* Improved debugging
  * Set a breakpoint anywhere


## Prerequisites

- Java 11
- Docker


## Getting started

Add to the plugins section of your spring boot project:

```gradle
plugins {
  id 'com.github.hmcts.rse-cft-lib' version '...'
}

```
## Launching your application + CCD
```gradle
./gradlew bootWithCCD
```

This will launch (in a single JVM):

* Your application
* CCD data, definition & user profile applications
* AM role assignment service

Plus docker containers:

* CCD & AM dependencies (postgres, logstash & elastic search
* XUI, available on http://localhost:3000


### :warning: Note to maintainers :warning:

This repository features a Gradle init script to customise the included CFT projects. gradlew has been modified to invoke this script so **any upgrade to the gradle wrapper must preserve this modification**.
