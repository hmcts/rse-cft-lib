package uk.gov.hmcts.rse.ccd.lib;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableMap;
import lombok.SneakyThrows;
import net.lingala.zip4j.ZipFile;
import org.zeroturnaround.exec.ProcessExecutor;

public class ComposeRunner implements DependencyProvider {
    static final String XUI_ENV_PREFIX = "RSE_LIB_XUI_ENV_";

    @Override
    public void start() {
        dockerBoot();
    }

    @SneakyThrows
    void dockerBoot() {

        var f = File.createTempFile("cftlib", "");
        URL u = getClass().getResource("/cftlib-compose.zip");
        try (InputStream i = u.openStream()) {
            Files.copy(i, f.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        var dir = Files.createTempDirectory("cftlib");
        new ZipFile(f).extractAll(dir.toString());
        writeEnvFile(dir.resolve("xui.env"), buildPrefixedEnvOverrides(System.getenv(), System.getProperties(),
            XUI_ENV_PREFIX));
        var args = new ArrayList<String>(List.of("docker", "compose", "-p", "cftlib", "up", "-d", "--remove-orphans"));
        // When running on a CI server ensure clean container builds.
        if (null != System.getenv("CI") || null != System.getenv("RSE_LIB_CLEAN_BOOT")) {
            args.add("--force-recreate");
            args.add("--renew-anon-volumes");
        }

        new ProcessExecutor().command(args)
            .environment(getEnvironmentVars())
            .redirectOutput(System.out)
            .redirectError(System.err)
            .directory(dir.toFile())
            .exitValueNormal()
            .timeout(90, TimeUnit.MINUTES)
            .execute();

    }

    static Map<String, String> buildPrefixedEnvOverrides(
        Map<String, String> env,
        Properties properties,
        String prefix
    ) {
        var output = new LinkedHashMap<String, String>();

        if (env != null) {
            for (var entry : env.entrySet()) {
                var key = entry.getKey();
                if (key != null && key.startsWith(prefix)) {
                    var value = entry.getValue();
                    var stripped = key.substring(prefix.length());
                    if (!stripped.isBlank() && value != null && !value.isBlank()) {
                        output.put(stripped, value);
                    }
                }
            }
        }

        if (properties != null) {
            for (var key : properties.stringPropertyNames()) {
                if (key.startsWith(prefix)) {
                    var value = properties.getProperty(key);
                    var stripped = key.substring(prefix.length());
                    if (!stripped.isBlank() && value != null && !value.isBlank()) {
                        output.put(stripped, value);
                    }
                }
            }
        }

        return output;
    }

    static void writeEnvFile(Path file, Map<String, String> values) throws Exception {
        if (values == null || values.isEmpty()) {
            Files.writeString(file, "", StandardCharsets.UTF_8);
            return;
        }
        var lines = values.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + escapeForComposeEnvFile(entry.getValue()))
            .collect(Collectors.joining("\n"));
        Files.writeString(file, lines + "\n", StandardCharsets.UTF_8);
    }

    static String escapeForComposeEnvFile(String value) {
        return value.replace("$", "$$");
    }

    private Map<String, String> getEnvironmentVars() {
        var builder = new ImmutableMap.Builder<String, String>();
        // Copy across System properties as environment variables to docker-compose.
        // Necessary since we set auto-managed AAT secrets as Java system properties and they must be env vars to be
        // read by docker.
        for (Object o : System.getProperties().keySet()) {
            builder.put(o.toString(), System.getProperty(o.toString()));
        }

        if ("localAuth".equals(System.getenv("RSE_LIB_AUTH-MODE"))) {
            var hostEnv = System.getenv("JVM_HOST");
            var host = "http://" + (
                hostEnv != null
                    ? hostEnv
                    : "host.docker.internal");
            var runtime = host + ":5062";
            var waTaskApi = host + ":8087";

            builder.putAll(Map.of(
                "XUI_S2S_URL", host + ":" + ControlPlane.getEnvVar("RSE_LIB_S2S_PORT", 8489),
                "XUI_IDAM_API_URL", runtime,
                "XUI_IDAM_LOGIN_URL", xuiIdamLoginUrl(System.getenv()),
                "SERVICES_WORK_ALLOCATION_TASK_API", waTaskApi,
                "HEALTH_WORK_ALLOCATION_TASK_API", waTaskApi + "/health",
                // TODO: placeholder to pass health checks
                "XUI_EM_DOCASSEMBLY_API", ControlPlane.getEnvVar("XUI_EM_DOCASSEMBLY_API", runtime),
                "XUI_DOCUMENTS_API", ControlPlane.getEnvVar("XUI_DOCUMENTS_API", runtime),
                "XUI_DOCUMENTS_API_V2", ControlPlane.getEnvVar("XUI_DOCUMENTS_API_V2", runtime)
            ));
        }
        return builder.build();
    }

    // The URL XUI redirects the browser to for login, which must be reachable from the browser
    // rather than from within the docker network.
    static String xuiIdamLoginUrl(Map<String, String> env) {
        return env.getOrDefault("XUI_IDAM_LOGIN_URL",
            env.getOrDefault("IDAM_SIMULATOR_BASE_URL_OUTSIDE_NETWORK", "http://localhost:5062"));
    }
}
