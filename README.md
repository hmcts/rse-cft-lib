# RSE CFT lib ![Java CI](https://github.com/hmcts/rse-cft-lib/workflows/Java%20CI/badge.svg) ![GitHub tag (latest SemVer)](https://img.shields.io/github/v/tag/hmcts/rse-cft-lib?label=release)


## Run your application + CCD in a single JVM

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
* Fast reload your application with [spring boot devtools](https://docs.spring.io/spring-boot/docs/current/reference/html/using.html#using.devtools) for productive development
* Inbuilt AAT secret management ([az cli](https://docs.microsoft.com/en-us/cli/azure/install-azure-cli) required)


## Prerequisites

- Java 11
- Docker
- [Azure CLI](https://docs.microsoft.com/en-us/cli/azure/install-azure-cli) (when using automated AAT secret management)

## Example integrations

- [No fault divorce](https://github.com/hmcts/nfdiv-case-api)
- [Adoption](https://github.com/hmcts/adoption-cos-api)

## Getting started

### Add Jitpack as a Gradle plugin repository

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

- A ```bootwithCCD``` task which launches
  - (in one JVM)
    - Your spring boot application
    - CCD Data store
    - CCD Definition store
    - CCD User profile
    - CCD Case document Access Management (CDAM)
    - Access Management role assignment service
    - Assign access to a case
  - (in docker)
    - Required dependencies (postgres, elasticsearch etc)
    - XUI Manage cases
    - XUI Manage org
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

A Java API is provided for interacting with CFT services to perform common tasks such as creating roles and importing CCD definitions.

This API is accessed by providing an implementation of the [CFTLibConfigurer](https://github.com/hmcts/rse-cft-lib/blob/main/lib/rse-cft-lib/src/main/java/uk/gov/hmcts/rse/ccd/lib/api/CFTLib.java) interface in the cftlib sourceset, which will be invoked by the library during startup once all CFT services are ready.

```java
@Component
public class CFTLibConfig implements CFTLibConfigurer {

  @Override
  public void configure(CFTLib lib) {
    // Create a CCD user profile
    lib.createProfile("banderous","DIVORCE", "NO_FAULT_DIVORCE", "Submitted");
    // Create roles
    lib.createRoles(
        "caseworker-divorce",
        ...
    );
    // Configure the AM role assignment service
    var json = Resources.toString(Resources.getResource("cftlib-am-role-assignments.json"), StandardCharsets.UTF_8);
    lib.configureRoleAssignments(json);
    
    // Import a CCD definition xlsx
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
* Assign access to a case

Plus (in docker):

* CCD & AM dependencies (postgres, logstash & elastic search
* XUI manage cases, available on http://localhost:3000
* XUI manage org, available on http://localhost:3001

### 4. Debugging

Launch with `--debug-jvm` and attach your debugger to debug your application plus all bundled cft services (with source level debugging).

### 5. Writing integration tests

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

### 6. Configuration

#### IDAM & S2S

Use either AAT's IDAM & S2S or local simulators, configurable via the ```authMode``` gradle task property.

Local

```groovy
bootWithCCD {
    // IDAM simulator will be started on port 5000,
    // S2S simulator on port 8489
    authMode = uk.gov.hmcts.rse.AuthMode.Local
}
```

AAT (VPN required)

```groovy
bootWithCCD {
    // No idam or s2s simulators will be started and services will be configured to point to AAT idam & s2s.
    authMode = uk.gov.hmcts.rse.AuthMode.AAT
}
```

Secrets for AAT dependencies are automatically pulled and configured (from a cftlib Azure keyvault into build/cftlib/.aat-env).

##### Overriding default S2S & IDAM ports

The default S2S port can be overridden by setting the `RSE_LIB_S2S_PORT` environment variable.


#### XUI LaunchDarkly client ID

XUI requires a valid LD client ID to function, which should be provided by setting the `XUI_LD_ID` environment variable.

#### Databases

##### Creating additional databases

If your application requires a database(s) then you can have the cftlib create them for you by setting the `RSE_LIB_ADDITIONAL_DATABASES` environment variable as a comma delimited value.

```
bootWithCCD {
    environment 'RSE_LIB_ADDITIONAL_DATABASES', 'my_db_1,my_db_2'
}
```

##### Accessing databases

Postgres is started on port 6432 (default) and can be accessed with user `postgres` password `postgres`

The default postgres port can be overridden by setting the `RSE_LIB_DB_PORT` environment variable.

##### Database names

| Service | Database name |
| ------- | ---- |
| CCD definition store | definitionstore |
| CCD data store | datastore |
| CCD user profile | userprofile |
| AM role assignment service | am |

eg. to connect to ccd data store db ```psql postgresql://postgres:postgres@localhost:6432/datastore```

#### Ports

Services run on the following default ports:

| Service | Port |
| ------- | ---- |
| CCD definition store | 4451 |
| CCD data store | 4452 |
| CCD user profile | 4453 |
| CCD case document Access Management | 4455 |
| AM role assignment service | 4096 |
| AAC assign access to a case | 4454 |
| XUI Manage cases | 4454 |
| XUI Manage org | 4454 |
| IDAM Simulator* | 5000 |
| S2S Simulator* | 8489 |

\* When running AuthMode.Local

### Clean boot

For a clean boot define the RSE_LIB_CLEAN_BOOT environment variable, which will force recreate all docker containers upon boot.

### Live reload

[Spring boot's devtools](https://docs.spring.io/spring-boot/docs/current/reference/html/using.html#using.devtools) can be used to fast-reload your application whilst leaving other CFT services running, significantly 
improving the edit-compile-test cycle.

```groovy
dependencies {
  cftlibImplementation 'org.springframework.boot:spring-boot-devtools'
}
```

With spring devtools on the classpath your application will automatically reload as you edit and build your java classes.

## How the cftlib works

The cftlib uses isolated classloaders to run multiple spring boot applications in a single Java Virtual Machine (JVM).

```mermaid
graph TD;
    boot[Bootstrap classloader]-->app[Your app's classloader];
    boot-->datastore[CCD data store classloader];
    boot-->definition[CCD definition store classloader];
    boot-->etc[etc...];
```

By running each spring boot application in its own classloader dependency conflicts are avoided; each application can have its own unique dependency set.

### Project structure

#### rse-cft-lib-plugin

The cftlib Gradle plugin that configures the build of the consuming project, creating the build tasks, sourcesets etc.

#### lib/

The lib folder contains libraries that are published to the jitpack maven repository and are consumed as dependencies when running the cftlib.

##### lib/bootstrapper

An application that creates each of the necessary classloaders to run our spring applications and defines the Cftlib API (but not its implementation). 

This project runs on the system classloader, meaning it is on the classpath to the JVM upon launch. Since it is on the system classloader it is also accessible to the isolated classloaders that run our applications (which have the system classloader as their parent).

A consequence of being on the system classloader is that this project should be dependency free; any dependencies present on the system classloader would override those in the isolated classloaders leading to potential conflicts.

##### lib/cftlib-agent

Added to the classpath of each spring boot application that the cftlib runs, enabling the injection of new & custom functionality.

For example, to coordinate the boot process a Spring boot event listener detects when each spring boot application is ready and reports it to the bootstrapper control plane.

##### lib/runtime

A minimal spring boot application that provides the CftLibApi implementation and s2s simulator.

##### lib/test-runner

Provides integration testing support using a junit runner. 

## Previous prototype ideas

### Run everything as a single Spring boot Application

Rather than running each cft service as an independent spring boot application, run a single spring boot application containing all the application code.

This falls down on the shared classpath; irreconcilable dependency conflicts can arise when two or more services share a dependency for which no mutually compatible version exists.

I encountered this with the Jackson library when prototyping this idea; one CFT service would only work with jackson version X and another with version Y, so they couldn't run under a single classloader where you can have only one version of jackson loaded.

pros: 
* Significant further reduction in resource requirements
* Faster boot times
cons: 
 dependency conflicts (terminal)
 colliding URLs; two different services might define the same URL mappings

### Extract fat jars from CNP pipeline docker images

Copy and run the complete fat jars from the docker images in the hmcts container registries.

cons:
 * Transient images - HMCTS container images are cleared down after a time
 * Classpath injection harder with fat jars


### :warning: Note to maintainers :warning:

This repository features a modified `gradlew`!

A Gradle init script is used to customise the included CFT projects. gradlew has been modified to invoke this script so **any upgrade to the gradle wrapper must preserve this modification**.

