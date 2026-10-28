package uk.gov.hmcts.rse;

import com.azure.identity.AzureCliCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import lombok.SneakyThrows;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.options.Option;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CftlibExec extends JavaExec {
    public AuthMode authMode = AuthMode.AAT;
    public DependencyMode dependencyMode = DependencyMode.Docker;
    // Optional parts of the stack this task runs, as Feature names. RSE_LIB_FEATURES adds more for one run.
    public List<String> features = new ArrayList<>();
    private List<String> testPatterns = List.of();

    public CftlibExec() {
        setJavaToolChain();
    }

    @Option(option = "tests", description = "Run matching cftlibTest classes; supports simple names, full names and *.")
    public void setTests(List<String> patterns) {
        if (!"cftlibTest".equals(getName())) {
            throw new InvalidUserDataException("--tests is supported by cftlibTest only.");
        }
        testPatterns = List.copyOf(patterns);
    }

    public void features(String... names) {
        features.addAll(Arrays.asList(names));
    }

    @Internal
    public Set<Feature> getResolvedFeatures() {
        return Feature.resolve(features, getEnvironment());
    }

    @Internal
    public List<String> getTestArguments() {
        if (testPatterns.isEmpty()) {
            return List.of("execute", "--select-package=uk.gov.hmcts");
        }
        String expression = testPatterns.stream().map(pattern -> {
            String regex = Pattern.quote(pattern).replace("*", "\\E.*\\Q");
            return pattern.contains(".") ? regex : "(?:.*[.$])?" + regex;
        }).collect(Collectors.joining("|"));
        return List.of("execute", "--select-package=uk.gov.hmcts",
            "--include-classname=" + expression, "--fail-if-no-tests");
    }

    @Override
    public void exec() {
        configure();
        super.exec();
    }

    private void configure() {
        setRequiredJvmArgs();
        setStandardEnvVars();
        // An inherited or explicitly configured value wins, so the mode can be flipped
        // from the environment without editing the build script.
        if (getEnvironment().get("RSE_LIB_DEPENDENCY_MODE") == null) {
            environment("RSE_LIB_DEPENDENCY_MODE", dependencyMode.name().toLowerCase());
        }
        setComposeProfiles();
        if (authMode == AuthMode.Local) {
            environment("RSE_LIB_AUTH-MODE", "localAuth");

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

    // Optional docker services: the XUI containers, which sit behind a compose profile so that runs which do not
    // want them, such as cftlibTest, do not pay for the image pull; those of the task's features; and any listed in
    // RSE_LIB_COMPOSE_PROFILES or COMPOSE_PROFILES.
    private void setComposeProfiles() {
        var profiles = new ArrayList<String>();
        if (wanted("RSE_LIB_XUI")) {
            profiles.add("xui");
        }
        if (wanted("RSE_LIB_XUI_MO")) {
            profiles.add("xui-mo");
        }
        var resolved = getResolvedFeatures();
        resolved.forEach(feature -> feature.composeProfiles.stream()
            .filter(profile -> !profiles.contains(profile))
            .forEach(profiles::add));
        // Read by the runtime, which prepares the features' dependencies such as their databases.
        environment("RSE_LIB_FEATURES", resolved.stream().map(Enum::name).collect(Collectors.joining(",")));
        // The task's environment: the shell's, plus anything the build script sets on the task.
        for (var var : List.of("RSE_LIB_COMPOSE_PROFILES", "COMPOSE_PROFILES")) {
            var requested = String.valueOf(getEnvironment().getOrDefault(var, ""));
            Arrays.stream(requested.split(","))
                .map(String::trim)
                .filter(profile -> !profile.isEmpty() && !profiles.contains(profile))
                .forEach(profiles::add);
        }
        environment("COMPOSE_PROFILES", String.join(",", profiles));
    }

    private boolean wanted(String var) {
        return !"off".equalsIgnoreCase(String.valueOf(getEnvironment().get(var)));
    }

    // All services share a Java 21 JVM.
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

        // Pin to a specific version of the .env file for reproducible builds.
        // This will need to be updated when the keyvault is modified.
        String version = "9965bd45e4b9480fbcef609a8a0151f3";

        var env = CftLibPlugin.cftlibBuildDir(getProject()).file(".aat-env-" + version).getAsFile();
        if (!env.exists()) {
            try (var os = new OutputStreamWriter(new FileOutputStream(getProject().file(env)))) {
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
                environment(key, value);
            }
        }

    }

    @SneakyThrows
    private void setStandardEnvVars() {
        // We use a URLClassLoader for running spring applications so we must set this for spring's devtools to activate
        systemProperty("spring.devtools.restart.enabled", true);
        environment("APPINSIGHTS_INSTRUMENTATIONKEY", "key");
        environment("CORE_CASE_DATA_API_URL", "http://localhost:4452");
        environment("RSE_LIB_LOG_FOLDER", getProject().getLayout()
                .getBuildDirectory().dir("cftlib/logs").get().getAsFile().getCanonicalPath());
        // Documents stored by the runtime's dm-store stand-in.
        environment("RSE_LIB_DM_STORE_DIR", getProject().getLayout()
                .getBuildDirectory().dir("cftlib/dm-store").get().getAsFile().getCanonicalPath());
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
