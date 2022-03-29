package uk.gov.hmcts.rse;

import java.util.List;

import org.gradle.api.tasks.JavaExec;

public class LibRunnerTask extends JavaExec {
  public AuthMode authMode = AuthMode.AAT;

  @Override
  public void exec() {
    configure();
    super.exec();
  }

  private void configure() {
    setRequiredJvmArgs();
    setStandardEnvVars();
    if (authMode == AuthMode.Local) {
      environment("RSE_LIB_AUTH-MODE", "localAuth");
      // Enable idam simulator
      environment("COMPOSE_PROFILES", "localAuth");

      // S2S simulator
      environment("IDAM_S2S-AUTH_URL", "http://localhost:8489");
      // Idam simulator
      environment("IDAM_API_URL", "http://localhost:5000");

      // Sets data store
      environment("CASE_DOCUMENT_AM_URL", "http://localhost:5000");

      environment("OIDC_ISSUER", "http://localhost:5000");
      environment("SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_OIDC_ISSUER_URI",
        "http://localhost:5000/o");
    } else {
      environment("SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_OIDC_ISSUER_URI",
        "https://idam-web-public.aat.platform.hmcts.net/o");
    }
//    environment("LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY", "DEBUG");
  }

  private void setStandardEnvVars() {

    environment("USER_PROFILE_DB_PORT", 6432);
    environment("USER_PROFILE_DB_USERNAME", "postgres");
    environment("USER_PROFILE_DB_PASSWORD", "postgres");
    environment("USER_PROFILE_DB_NAME", "userprofile");
    environment("APPINSIGHTS_INSTRUMENTATIONKEY", "key");

    environment("DATA_STORE_DB_PORT", 6432);
    environment("DATA_STORE_DB_USERNAME", "postgres");
    environment("DATA_STORE_DB_PASSWORD", "postgres");
    environment("DATA_STORE_DB_NAME", "datastore");

    environment("DEFINITION_STORE_DB_PORT", 6432);
    environment("DEFINITION_STORE_DB_USERNAME", "postgres");
    environment("DEFINITION_STORE_DB_PASSWORD", "postgres");
    environment("DEFINITION_STORE_DB_NAME", "definitionstore");

    environment("ROLE_ASSIGNMENT_DB_HOST", "localhost");
    environment("ROLE_ASSIGNMENT_DB_PORT", "6432");
    environment("ROLE_ASSIGNMENT_DB_NAME", "am");
    environment("ROLE_ASSIGNMENT_DB_USERNAME", "postgres");
    environment("ROLE_ASSIGNMENT_DB_PASSWORD", "postgres");

    environment("SEARCH_ELASTIC_HOSTS", "http://localhost:9200");
    environment("SEARCH_ELASTIC_DATA_HOSTS", "http://localhost:9200");
    environment("ELASTICSEARCH_ENABLED", "true");
    environment("ELASTICSEARCH_FAILIMPORTIFERROR", "true");

    // Allow more time for definitions to import to reduce test flakeyness
    environment("CCD_TX-TIMEOUT_DEFAULT", "120");
  }

  private void setRequiredJvmArgs() {
    jvmArgs("-Xverify:none");
    jvmArgs("-XX:TieredStopAtLevel=1");

    // Required by Access Management for Java 17.
    // https://github.com/x-stream/xstream/issues/101
    List.of("java.lang", "java.util", "java.lang.reflect", "java.text", "java.awt.font").forEach(x -> {
      jvmArgs("--add-opens", "java.base/" + x + "=ALL-UNNAMED");
    });
    jvmArgs("--add-opens", "java.desktop/java.awt.font=ALL-UNNAMED");
    jvmArgs("-XX:ReservedCodeCacheSize=64m");
  }
}
