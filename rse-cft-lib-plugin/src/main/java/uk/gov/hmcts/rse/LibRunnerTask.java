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
            environment("IDAM_S2S-AUTH_URL", "http://localhost:${RSE_LIB_S2S_PORT:8489}");

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
    }

    private void setStandardEnvVars() {
        // We use a URLClassLoader for running spring applications so we must set this for spring's devtools to activate
        systemProperty("spring.devtools.restart.enabled", true);
        environment("APPINSIGHTS_INSTRUMENTATIONKEY", "key");
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
        jvmArgs("-XX:ReservedCodeCacheSize=128m");
    }
}
