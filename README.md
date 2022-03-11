# RSE CFT lib ![Java CI](https://github.com/hmcts/rse-cft-lib/workflows/Java%20CI/badge.svg) ![GitHub tag (latest SemVer)](https://img.shields.io/github/v/tag/hmcts/rse-cft-lib?label=release)

## Run multiple HMCTS services in a single JVM

### Rationale

Improved local development and robust automated tests:

* Reduced RAM requirements & improved performance
* Improved debugging
  * Set a breakpoint anywhere in any included CFT service
* A Java API for:
  * Definition imports
  * Role creation
* Includes a test runner for automated integration tests
* Simple setup


## Prerequisites

- Java 11
- Docker

## Example integrations

- [No fault divorce](https://github.com/hmcts/nfdiv-case-api)
- [Adoption](https://github.com/hmcts/adoption-cos-api)


## Getting started

### Add Jitpack as a plugin repository

The plugin is hosted on [jitpack](https://jitpack.io/) so you must add the following to your project's `settings.gradle`; 

```gradle
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven {
            url "https://jitpack.io"
        }
    }
}
```

### 1. Integrate the Gradle plugin in your build script

```gradle
plugins {
  id 'com.github.hmcts.rse-cft-lib' version '[@top of page]'
}
```



This will define the following in your Gradle build:

- A ```bootwithCCD``` task
  - Launches your Application + CCD + Access Management
- A `cftlibTest` task
  - Run automated CCD integration tests  
- Sourcesets
  - `cftlib`
    - For code that should run when running with CCD 
  - `cftlibTest`
    - For integration tests
- Dependency configurations
  - `cftlibImplementation`
    - For dependencies you need when running with CCD
  - `cftlibTestImplementation`
    - For integration test dependencies

### 2. Define your CFTLib configuration

A Java API is provided for interacting with CFT services and performing common tasks such as creating roles and importing CCD definitions.

This API is accessed by providing an implementation of the [CFTLibConfigurer](https://github.com/hmcts/rse-cft-lib/blob/main/lib/rse-cft-lib/src/main/java/uk/gov/hmcts/rse/ccd/lib/api/CFTLib.java) interface in the cftlib sourceset, which will be invoked by the library during startup once all CFT services are ready.

```java
@Component
public class CFTLibConfig implements CFTLibConfigurer {

  @Override
  public void configure(CFTLib lib) {
    lib.createProfile("banderous","DIVORCE", "NO_FAULT_DIVORCE", "Submitted");
    lib.createRoles(
        "caseworker-divorce",
        ...
    );
    var json = Resources.toString(Resources.getResource("cftlib-am-role-assignments.json"), StandardCharsets.UTF_8);
    lib.configureRoleAssignments(json);

    var def = getClass().getClassLoader().getResourceAsStream("NFD-dev.xlsx").readAllBytes();
    lib.importDefinition(def);
  }
}
```

Note that your CFTLibConfigurer implementation must be in the cftlib sourceset.

### 3. Launch your application + CCD
```gradle
./gradlew bootWithCCD
```

This will launch (in a single JVM):

* Your application
* CCD data, definition & user profile services
* AM role assignment service

Plus (in docker):

* CCD & AM dependencies (postgres, logstash & elastic search
* XUI, available on http://localhost:3000

### 4. Writing integration tests

A `CftlibTest` junit base class is provided for writing robust automated integration tests that test your application end-to-end with CCD.

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestWithCCD extends CftlibTest {
    @Test
    public void bootsWithCCD() {
    }
}
```

Tests must be placed in the `cftlibTest` sourceset.

### :warning: Note to maintainers :warning:

This repository features a Gradle init script to customise the included CFT projects. gradlew has been modified to invoke this script so **any upgrade to the gradle wrapper must preserve this modification**.
