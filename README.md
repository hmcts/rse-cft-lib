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

Add to the plugins section of your build:

```gradle
plugins {
  id 'com.github.hmcts.rse-cft-lib' version '...'
}

```

```gradle
./gradlew bootWithCCD
```


XUI will be launched on http://localhost:3000


### :warning: Modified Gradle wrapper :warning:

This repository features a Gradle init script to customise the included CFT projects. gradlew has been modified to invoke this script so any upgrade to the gradle wrapper must preserve this modification.
