package uk.gov.hmcts.rse;

import com.azure.identity.AzureCliCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import lombok.SneakyThrows;
import org.gradle.api.Project;
import org.gradle.process.JavaForkOptions;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.util.List;

final class CftlibForkOptions {
    private CftlibForkOptions() {
    }

    static void configure(Project project, JavaForkOptions options, AuthMode authMode) {
        setRequiredJvmArgs(options);
        setStandardEnvVars(project, options);
        if (authMode == AuthMode.Local) {
            options.environment("RSE_LIB_AUTH-MODE", "localAuth");
            // Enable idam simulator
            options.environment("COMPOSE_PROFILES", "localAuth");

            // S2S simulator
            options.environment("IDAM_S2S-AUTH_URL", "http://localhost:${RSE_LIB_S2S_PORT:8489}");
            // Required by CDAM
            options.environment("S2S_URL", "http://localhost:${RSE_LIB_S2S_PORT:8489}");

            // Idam simulator
            options.environment("IDAM_API_URL", "${IDAM_SIMULATOR_BASE_URL:http://localhost:5062}");

            options.environment("CASE_DOCUMENT_AM_URL", "http://localhost:4455");

            options.environment("OIDC_ISSUER", "${IDAM_API_URL}");
            options.environment("SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_OIDC_ISSUER_URI",
                "${IDAM_API_URL}/o");
        } else {
            // AAT
            fetchAndSetAATSecrets(project, options);
            options.environment("SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_OIDC_ISSUER_URI",
                "https://idam-web-public.aat.platform.hmcts.net/o");
        }
    }

    @SneakyThrows
    private static void fetchAndSetAATSecrets(Project project, JavaForkOptions options) {
        // Cannot pull secrets running on continuous integration
        if (System.getenv("CI") != null) {
            return;
        }

        // Pin to a specific version of the .env file for reproducible builds.
        // This will need to be updated when the keyvault is modified.
        String version = "9965bd45e4b9480fbcef609a8a0151f3";

        var env = CftLibPlugin.cftlibBuildDir(project).file(".aat-env-" + version).getAsFile();
        if (!env.exists()) {
            try (var os = new OutputStreamWriter(new FileOutputStream(project.file(env)))) {
                SecretClient secretClient = new SecretClientBuilder()
                        .credential(new AzureCliCredentialBuilder().build())
                        .vaultUrl("https://rse-cft-lib.vault.azure.net")
                        .buildClient();

                KeyVaultSecret secret = secretClient.getSecret("aat-env", version);

                os.write(secret.getValue());
            }
        }

        var lines = Files.readAllLines(env.toPath());
        for (String line : lines) {
            var index = line.indexOf("=");
            if (index != -1) {
                var key = line.substring(0, index);
                var value = line.substring(index + 1);
                options.environment(key, value);
            }
        }

    }

    @SneakyThrows
    private static void setStandardEnvVars(Project project, JavaForkOptions options) {
        options.environment("APPINSIGHTS_INSTRUMENTATIONKEY", "key");
        options.environment("CORE_CASE_DATA_API_URL", "http://localhost:4452");
        options.environment("RSE_LIB_LOG_FOLDER", project.getLayout()
                .getBuildDirectory().dir("cftlib/logs").get().getAsFile().getCanonicalPath());
    }

    private static void setRequiredJvmArgs(JavaForkOptions options) {
        options.jvmArgs("-Xverify:none");
        options.jvmArgs("-XX:TieredStopAtLevel=1");

        // Required by Access Management for Java 17.
        // https://github.com/x-stream/xstream/issues/101
        List.of("java.lang", "java.util", "java.lang.reflect", "java.text", "java.awt.font").forEach(x -> {
            options.jvmArgs("--add-opens", "java.base/" + x + "=ALL-UNNAMED");
        });
        options.jvmArgs("--add-opens", "java.desktop/java.awt.font=ALL-UNNAMED");
        options.jvmArgs("-XX:ReservedCodeCacheSize=128m");
    }
}
