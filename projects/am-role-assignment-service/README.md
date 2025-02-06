# role-assignment-service

[![API Docs](https://img.shields.io/badge/API%20Docs-site-e140ad.svg)](https://hmcts.github.io/cnp-api-docs/swagger.html?url=https://hmcts.github.io/cnp-api-docs/specs/am-role-assignment-service.json)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=am-role-assignment-service&metric=alert_status)](https://sonarcloud.io/summary/overall?id=am-role-assignment-service)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=am-role-assignment-service&metric=security_rating)](https://sonarcloud.io/summary/overall?id=am-role-assignment-service)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=am-role-assignment-service&metric=vulnerabilities)](https://sonarcloud.io/summary/overall?id=am-role-assignment-service)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=am-role-assignment-service&metric=sqale_rating)](https://sonarcloud.io/summary/overall?id=am-role-assignment-service)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=am-role-assignment-service&metric=coverage)](https://sonarcloud.io/summary/overall?id=am-role-assignment-service)

Role Assignment Service

## Purpose

This SpringBoot application covers the implementation of the Role Assignment Service, which manages the assignment of roles with attributes to actors,
to support both ccd access control and work allocation requirements.
1) The core service manages role assignments, including both case roles and organisational roles.  The service is responsible for robust validation of role assignments, against a set of configured rules and/or configuration.
2) Role assignments are made available through a queryable API to two major consumers: the Case Access Control and Work Allocation.


### Prerequisites

To run the project you will need to have the following installed:

* Java 11
* Docker (optional)

For information about the software versions used to build this API and a complete list of it's dependencies see build.gradle

#### Environment variables
The following environment variables are required:

| Name | Default | Description |
|------|---------|-------------|
      |ROLE_ASSIGNMENT_S2S_AUTHORISED_SERVICES| ccd_gw,am_role_assignment_service,am_org_role_mapping_service,wa_task_management_api,aac_manage_case_assignment,ccd_data|
      |AM_ROLE_ASSIGNMENT_SERVICE_SECRET|
      |IDAM_USER_URL| http://idam-api:5000 |
      |IDAM_S2S_URL| http://service-auth-provider-api:8080|
      |ROLE_ASSIGNMENT_IDAM_CLIENT_ID|am_docker|
      |ROLE_ASSIGNMENT_IDAM_CLIENT_SECRET|am_docker_secret|
      |ROLE_ASSIGNMENT_IDAM_REDIRECT_URI|http://localhost:4096/oauth2redirect|
      |ROLE_ASSIGNMENT_IDAM_ADMIN_USERID|TEST_AM_USER2_BEFTA@test.local|
      |ROLE_ASSIGNMENT_IDAM_ADMIN_PASSWORD|Pa55word11|
      |ROLE_ASSIGNMENT_IDAM_ADMIN_SCOPE|search-user|
      |CCD_DATA_STORE_URL|http://localhost:4452|
      |LAUNCH_DARKLY_ENV|local|
      |LD_SDK_KEY|"Please contact the AM team for getting this key"|

## Building the application

The project uses [Gradle](https://gradle.org) as a build tool. It already contains
`./gradlew` wrapper script, so there's no need to install gradle.

To build the project execute the following command:

```bash
  ./gradlew build
```
To clean up your environment use the following, it will delete any temporarily generated files such as reports.

```bash
  ./gradlew clean
```
### Running

If you want your code to become available to other Docker projects (e.g. for local environment testing), you need to build the image:

```bash
docker-compose build
```

When the project has been packaged in `target/` directory,
you can run it by executing following command:

```bash
docker-compose up
```
Note: This setup requires both IDAM and serviceAuth application to be running in Local docker network 'am-docker'. Please follow the am-docker project to setup these dependencies.

As a result the following containers will get created and started:

 - API exposing port `4096`

Alternatively, you can start the application from the current source files using Gradle as follows:

 ```
 ./gradlew clean bootRun
 ```

If required, to run with a low memory consumption, the following can be used:

 ```
 ./gradlew --no-daemon assemble && java -Xmx384m -jar build/libs/role-assoignment-service.jar
 ```

### Using the application

To understand if the application is working, you can call it's health endpoint:

```
curl http://localhost:4096/health
```

If the API is running, you should see this response:

```
{"status":"UP"}
```

### DB Initialisation˙

The application uses a Postgres database which can be run through a docker container on its own if required.

The application should automatically apply any database migrations using liquibase.

### Running integration tests:


You can run the *integration tests* as follows:

```
./gradlew integration
```

### Running functional tests:

If the API is running (either inside a Docker container or via `gradle bootRun`) you can run the *functional tests* as follows:

```
./gradlew functional
```

### Running smoke tests:

If the API is running (either inside a Docker container or via `gradle bootRun`) you can run the *smoke tests* as follows:

```
./gradlew smoke
```

### Running mutation tests tests:

If you have some time to spare, you can run the *mutation tests* as follows:

```
./gradlew pitest
 ```
If you are using windows machine to run PI test , use following property in gradle.build under pitest section.
```
 useClasspathFile = true
```

As the project grows, these tests will take longer and longer to execute but are useful indicators of the quality of the test suite.

More information about mutation testing can be found here:
http://pitest.org/



### Contract testing with pact

To publish against remote broker:
`./gradlew pactPublish`

Turn on VPN and verify on url `https://pact-broker.platform.hmcts.net/`
The pact contract(s) should be published


Remember to return the localhost back to the remote broker

## Endpoints

Authorization(OIDC user token) and ServiceAuthorization (S2S) tokens are required in the headers for all endpoints.

```
POST /am/role-assignments
```
- Used for creating multiple role assignments records.
Also requires a request body payload containing:
 1) roleRequest
 2) requestedRoles

```
GET /am/role-assignments/actors/{actorId}
```
- Retrieve JSON representation of multiple Role Assignment records.

```
GET ​/am​/role-assignments
```
- Get Role assignment records by Case Id and Actor Id for RoleType as a CASE.

```
GET /am/role-assignments/roles
```
- Retrieves a list of roles available in role assignment service.

```
DELETE /am/role-assignments/
```
- Deletes multiple role assignment based on query parameters. Also requires following request params
1) process {string}
2) reference {string}

```
DELETE /am/role-assignments/{assignmentId}
```
- Deletes single role assignment by assignment Id.

### Functional Tests
The functional tests are located in `functionalTest` folder. These are the tests run against an environment. For example if you would
like to test your local environment you'll need to export the following variables on your `.bash_profile` script.


```bash
#Functional Tests
export BEFTA_S2S_CLIENT_ID=am_role_assignment_service
export BEFTA_S2S_CLIENT_SECRET=AAAAAAAAAAAAAAAC
export BEFTA_RESPONSE_HEADER_CHECK_POLICY=JUST_WARN
export OAUTH2_CLIENT_ID=am_docker
export OAUTH2_CLIENT_SECRET=am_docker_secret
export OAUTH2_ACCESS_TOKEN_TYPE=OIDC
export OAUTH2_SCOPE_VARIABLES=openid%20profile%20roles%20authorities
export OAUTH2_REDIRECT_URI=http://localhost:4096/oauth2redirect
export IDAM_CLIENT_ID=am_role_assignment
export OPENID_SCOPE_VARIABLES =openid+profile+roles+authorities
export TEST_AM_USER1_BEFTA_PWD=Pa55word11
export TEST_AM_USER2_BEFTA_PWD=Pa55word11
export TEST_AM_USER3_BEFTA_PWD=Pa55word11

```


####Running the tests

In order to run the tests you will need to pull down ```am-docker``` repo and checkout the ```master``` branch.

Run the scripts as instructed in am-docker page .
Once this is done, try to run your functional tests.



## LICENSE

This project is licensed under the MIT License - see the [LICENSE](LICENSE.md) file for details.

