package uk.gov.hmcts.rse.ccd.lib;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableMap;
import lombok.SneakyThrows;
import net.lingala.zip4j.ZipFile;
import org.zeroturnaround.exec.ProcessExecutor;

public class ComposeRunner implements DependencyProvider {

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
        writeXuiEnvFiles(dir);
        // Docker mounts the emulator's config whether or not the servicebus profile is on, so always write it.
        ServiceBusConfig.write(dir.resolve("servicebus/Config.json"), System.getenv());
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

    /**
     * The XUI services take their whole environment from these files rather than from a compose
     * environment block, so that the process launcher and compose configure XUI identically.
     */
    static void writeXuiEnvFiles(Path dir) throws Exception {
        var source = XuiEnvironment.interpolationSource();
        var network = XuiEnvironment.Network.DOCKER;
        writeEnvFile(dir.resolve("xui.env"),
            XuiApp.MANAGE_CASES.environment(source, network, XuiApp.MANAGE_CASES.port(source, network)));
        writeEnvFile(dir.resolve("xui-mo.env"),
            XuiApp.MANAGE_ORG.environment(source, network, XuiApp.MANAGE_ORG.port(source, network)));
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
        return builder.build();
    }
}
