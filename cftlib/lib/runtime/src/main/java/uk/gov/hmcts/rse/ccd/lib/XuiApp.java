package uk.gov.hmcts.rse.ccd.lib;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * The two XUI apps cftlib can run, and everything that differs between them.
 *
 * <p>The pinned repository, revision and node requirement come from xui-versions.properties and
 * can each be overridden for a single task by environment variable.
 */
enum XuiApp {

    MANAGE_CASES(
        "xui-manage-cases",
        "manage-cases",
        "RSE_LIB_XUI",
        "XUI_PORT",
        3000,
        List.of("install", "--immutable", "--inline-builds"),
        List.of("./dist/rpx-exui/api/server.bundle.js")),

    MANAGE_ORG(
        "xui-manage-org",
        "manage-org",
        "RSE_LIB_XUI_MO",
        "XUI_MO_PORT",
        3001,
        List.of("install", "--immutable", "--mode=skip-build"),
        List.of("--enable-source-maps", "./dist/rpx-xui-manage-organisations/api/server.bundle.js"));

    private static final Properties PINS = loadPins();

    private final String appName;
    private final String slug;
    private final String envPrefix;
    private final String portVariable;
    private final int defaultPort;
    private final List<String> installArgs;
    private final List<String> startArgs;

    XuiApp(String appName, String slug, String envPrefix, String portVariable, int defaultPort,
           List<String> installArgs, List<String> startArgs) {
        this.appName = appName;
        this.slug = slug;
        this.envPrefix = envPrefix;
        this.portVariable = portVariable;
        this.defaultPort = defaultPort;
        this.installArgs = installArgs;
        this.startArgs = startArgs;
    }

    String appName() {
        return appName;
    }

    String slug() {
        return slug;
    }

    /**
     * The variable that selects how this app is run, which is also the prefix of its others.
     */
    String variable() {
        return envPrefix;
    }

    /** The variable that moves this app off its default port. */
    String portVariable() {
        return portVariable;
    }

    List<String> installArgs() {
        return installArgs;
    }

    /**
     * The arguments to node, run from the checkout root so that node-config finds config/.
     */
    List<String> startArgs() {
        return startArgs;
    }

    /**
     * The port the app listens on. In docker this is the container port the published port maps to,
     * so it is fixed; on the host the app binds the published port directly.
     */
    int port(Map<String, String> source, XuiEnvironment.Network network) {
        if (network == XuiEnvironment.Network.DOCKER) {
            return defaultPort;
        }
        var configured = source.get(portVariable);
        if (configured == null || configured.isBlank()) {
            return defaultPort;
        }
        return Integer.parseInt(configured.trim());
    }

    String repoUrl(Map<String, String> source) {
        return setting(source, "_REPO", "repo");
    }

    String ref(Map<String, String> source) {
        return setting(source, "_REF", "ref");
    }

    String nodeRange(Map<String, String> source) {
        return setting(source, "_NODE_RANGE", "node");
    }

    /**
     * An existing checkout to build and run instead of cftlib's own shallow clone.
     */
    String checkoutOverride(Map<String, String> source) {
        var dir = source.get(envPrefix + "_DIR");
        return dir == null || dir.isBlank() ? null : dir.trim();
    }

    Map<String, String> environment(Map<String, String> source, XuiEnvironment.Network network, int port) {
        return this == MANAGE_CASES
            ? XuiEnvironment.manageCases(source, network, port)
            : XuiEnvironment.manageOrg(source, network, port);
    }

    private String setting(Map<String, String> source, String envSuffix, String pin) {
        var override = source.get(envPrefix + envSuffix);
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        var pinned = PINS.getProperty(slug + "." + pin);
        if (pinned == null || pinned.isBlank()) {
            throw new IllegalStateException(
                "xui-versions.properties has no " + slug + "." + pin + " for " + appName);
        }
        return pinned.trim();
    }

    private static Properties loadPins() {
        var properties = new Properties();
        try (var stream = XuiApp.class.getResourceAsStream("/xui-versions.properties")) {
            if (stream == null) {
                throw new IllegalStateException("xui-versions.properties is missing from the cftlib runtime jar");
            }
            properties.load(stream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return properties;
    }
}
