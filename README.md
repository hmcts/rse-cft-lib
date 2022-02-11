# PoC

## Prerequisites

- Java 11
- Docker

## Getting started

Dependencies of this project are using Git submodules, fetch them with:

```shell
git submodule update --init
```

You can then start the application with:

```shell
./gradlew bootRun
```

This may take quite awhile the first time as there's lots of dependencies to be downloaded across the sub-projects.

You'll eventually get an application running on `http://localhost:8234/`.

A Postgres DB will be started for you by testcontainers as well.

### Tests

Take a look at the [lib-consumer](lib-consumer) project to see tests that show the full workflow for creating a case done completely in a Spring web context.

### :warning: Modified Gradle wrapper :warning:

This repository features a Gradle init script to customise the included CFT projects. gradlew has been modified to invoke this script so any upgrade to the gradle wrapper must preserve this modification.
