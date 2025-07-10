package uk.gov.hmcts.rse;

import com.azure.identity.AzureCliCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import lombok.SneakyThrows;
import org.gradle.api.tasks.JavaExec;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;

import java.util.Arrays;
import java.util.List;

public class CftlibExec extends JavaExec {
    public AuthMode authMode = AuthMode.AAT;

    public CftlibExec() {
        setJavaToolChain();
    }

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
            // Required by CDAM
            environment("S2S_URL", "http://localhost:${RSE_LIB_S2S_PORT:8489}");

            // Idam simulator
            environment("IDAM_API_URL", "${IDAM_SIMULATOR_BASE_URL:http://localhost:5062}");

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

    // Java 21 is now required since cft projects are using java 21 features.
    private void setJavaToolChain() {
        var launcher = getProject().getExtensions().getByType(JavaToolchainService.class)
            .launcherFor(x -> {
                x.getLanguageVersion().set(JavaLanguageVersion.of("21"));
            });
        this.getJavaLauncher().set(launcher);
    }

    @SneakyThrows
    private void fetchAndSetAATSecrets() {
        // Cannot pull secrets running on continuous integration
        if (System.getenv("CI") != null) {
            return;
        }

        SecretClient secretClient = new SecretClientBuilder()
                .credential(new AzureCliCredentialBuilder().build())
                .vaultUrl("https://rse-cft-lib.vault.azure.net")
                .buildClient();

        // Pin to a specific version of the .env file for reproducible builds.
        // This will need to be updated when the keyvault is modified.
        String secretVersion = "3aa0d793f49049f682aac07c490cc166";
        KeyVaultSecret secret = secretClient.getSecret("aat-env", secretVersion);

        String[] lines = secret.getValue().split("\n");
        Arrays.stream(lines)
                .forEach(line -> {
                    var index = line.indexOf("=");
                    if (index != -1) {
                        var key = line.substring(0, index);
                        var value = line.substring(index + 1);
                        environment(key, value);
                    }
                });

    }

    @SneakyThrows
    private void setStandardEnvVars() {
        // We use a URLClassLoader for running spring applications so we must set this for spring's devtools to activate
        systemProperty("spring.devtools.restart.enabled", true);
        environment("APPINSIGHTS_INSTRUMENTATIONKEY", "key");
        environment("CORE_CASE_DATA_API_URL", "http://localhost:4452");
        environment("RSE_LIB_LOG_FOLDER", getProject().getLayout()
                .getBuildDirectory().dir("cftlib/logs").get().getAsFile().getCanonicalPath());
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
