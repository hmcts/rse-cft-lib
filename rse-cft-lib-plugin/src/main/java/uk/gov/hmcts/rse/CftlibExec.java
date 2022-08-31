package uk.gov.hmcts.rse;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import lombok.SneakyThrows;
import org.apache.tools.ant.taskdefs.condition.Os;
import org.gradle.api.tasks.JavaExec;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;

public class CftlibExec extends JavaExec {
    public AuthMode authMode = AuthMode.AAT;

    @Override
    public void exec() {
        configure();
        super.exec();
    }

    private void configure() {
        setJavaToolChain();
        setRequiredJvmArgs();
        setStandardEnvVars();
        if (authMode == AuthMode.Local) {
            environment("RSE_LIB_AUTH-MODE", "localAuth");
            // Enable idam simulator
            environment("COMPOSE_PROFILES", "localAuth");

            // S2S simulator
            environment("IDAM_S2S-AUTH_URL", "http://localhost:${RSE_LIB_S2S_PORT:8489}");
            // Required by CDAM
            environment("S2S_URL", "http://localhost:${RSE_LIB_S2S_PORT:8489}");

            // Idam simulator
            environment("IDAM_API_URL", "${IDAM_SIMULATOR_BASE_URL:http://localhost:5000}");

            environment("CASE_DOCUMENT_AM_URL", "http://localhost:4455");

            environment("OIDC_ISSUER", "${IDAM_API_URL}");
            environment("SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_OIDC_ISSUER_URI",
                "${IDAM_API_URL}/o");
        } else {
            // AAT
            fetchAndSetAATSecrets();
            environment("SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_OIDC_ISSUER_URI",
                "https://idam-web-public.aat.platform.hmcts.net/o");
        }
    }

    // Java 17 is now required since cft projects are using java 17 features.
    private void setJavaToolChain() {
        var launcher = getProject().getExtensions().getByType(JavaToolchainService.class)
            .launcherFor(x -> {
                x.getLanguageVersion().set(JavaLanguageVersion.of("17"));
            });
        this.getJavaLauncher().set(launcher);
    }

    @SneakyThrows
    private void fetchAndSetAATSecrets() {
        // Cannot pull secrets running on continuous integration
        if (System.getenv("CI") != null) {
            return;
        }

        var env = CftLibPlugin.cftlibBuildDir(getProject()).file(".aat-env").getAsFile();
        if (!env.exists()) {
            try (var os = new FileOutputStream(getProject().file(env))) {
                var cmd  = new ArrayList<>(List.of("az", "keyvault", "secret", "show", "-o", "tsv", "--query", "value",
                    "--id", "https://rse-cft-lib.vault.azure.net/secrets/aat-env"));
                // TODO: use the Azure java client library for cross platform secret retrieval
                if (Os.isFamily(Os.FAMILY_WINDOWS)) {
                    cmd.addAll(0, List.of("cmd", "/c"));
                }
                getProject().exec(x -> {
                    x.commandLine(cmd);
                    x.setStandardOutput(os);
                });
            }
        }

        var lines = Files.readAllLines(env.toPath());
        for (String line : lines) {
            var index = line.indexOf("=");
            if (index != -1) {
                var key = line.substring(0, index);
                var value = line.substring(index + 1);
                System.setProperty(key, value);
            }
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
