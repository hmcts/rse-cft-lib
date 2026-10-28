package uk.gov.hmcts.rse.ccd.lib;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The command lines cftlib uses to clone, build and run an XUI app on the host.
 *
 * <p>Pure so that the argument construction can be tested without a node or git on the machine.
 */
final class XuiCommands {

    /**
     * The install time variables the XUI Dockerfiles set to stop the node toolchain downloading
     * browsers we will never run.
     */
    static final Map<String, String> INSTALL_ENVIRONMENT = Map.ofEntries(
        Map.entry("PUPPETEER_SKIP_DOWNLOAD", "1"),
        Map.entry("PUPPETEER_SKIP_CHROMIUM_DOWNLOAD", "1"),
        Map.entry("CHROMEDRIVER_SKIP_DOWNLOAD", "1"),
        Map.entry("CYPRESS_INSTALL_BINARY", "0"),
        Map.entry("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1"),
        Map.entry("PLAYWRIGHT_BROWSERS_PATH", "0"),
        Map.entry("SENTRYCLI_SKIP_DOWNLOAD", "1"),
        Map.entry("NPM_CONFIG_FUND", "false"),
        Map.entry("NPM_CONFIG_AUDIT", "false"),
        Map.entry("NPM_CONFIG_UPDATE_NOTIFIER", "false"),
        Map.entry("SCARF_ANALYTICS", "false"));

    /**
     * The host variables the XUI process keeps. Everything else it needs is computed by
     * {@link XuiEnvironment}, so that a host process sees the same environment as a container.
     */
    static final List<String> INHERITED_ENVIRONMENT_KEYS = List.of(
        "PATH", "HOME", "USER", "LANG", "LC_ALL", "TMPDIR", "TMP", "TEMP", "SystemRoot",
        "NODE_EXTRA_CA_CERTS", "SSL_CERT_FILE", "HTTP_PROXY", "HTTPS_PROXY", "NO_PROXY",
        "http_proxy", "https_proxy", "no_proxy");

    private static final Pattern COMMIT_SHA = Pattern.compile("[0-9a-fA-F]{40}");
    private static final Pattern YARN_PATH = Pattern.compile("(?m)^yarnPath:\\s*\"?([^\"\\s]+)\"?\\s*$");

    private XuiCommands() {
    }

    static boolean isCommitSha(String ref) {
        return COMMIT_SHA.matcher(ref).matches();
    }

    /**
     * Creates a shallow checkout of ref. A sha cannot be given to {@code clone --branch}, so it is
     * fetched directly instead, which GitHub allows and which keeps the pin exact.
     */
    static List<List<String>> cloneCommands(String url, String ref, Path dir) {
        if (!isCommitSha(ref)) {
            return List.of(List.of("git", "clone", "--depth", "1", "--branch", ref, url, dir.toString()));
        }
        return List.of(
            List.of("git", "init", "--quiet", dir.toString()),
            List.of("git", "-C", dir.toString(), "remote", "add", "origin", url),
            List.of("git", "-C", dir.toString(), "fetch", "--depth", "1", "origin", ref),
            List.of("git", "-C", dir.toString(), "checkout", "--quiet", "--detach", "FETCH_HEAD"));
    }

    /**
     * Moves an existing cftlib clone onto ref.
     */
    static List<List<String>> fetchCommands(String url, String ref, Path dir) {
        return List.of(
            List.of("git", "-C", dir.toString(), "remote", "set-url", "origin", url),
            List.of("git", "-C", dir.toString(), "fetch", "--depth", "1", "origin", ref),
            List.of("git", "-C", dir.toString(), "checkout", "--quiet", "--detach", "FETCH_HEAD"));
    }

    static List<String> headCommand(Path dir) {
        return List.of("git", "-C", dir.toString(), "rev-parse", "HEAD");
    }

    /**
     * Asks only whether a commit is checked out. {@code --verify --quiet} exits 1 and says
     * nothing when HEAD does not resolve, which separates a clone that holds no commit from a
     * repository git refused to read at all, such as one it considers dubiously owned.
     */
    static List<String> headIfAnyCommand(Path dir) {
        return List.of("git", "-C", dir.toString(), "rev-parse", "--verify", "--quiet", "HEAD");
    }

    /**
     * Runs the yarn release the checkout vendors, so neither corepack nor a global yarn is needed.
     */
    static List<String> yarn(String node, Path yarnRelease, List<String> args) {
        var command = new ArrayList<String>();
        command.add(node);
        command.add(yarnRelease.toString());
        command.addAll(args);
        return command;
    }

    static List<String> startCommand(String node, XuiApp app) {
        var command = new ArrayList<String>();
        command.add(node);
        command.addAll(app.startArgs());
        return command;
    }

    /**
     * The vendored yarn release path, read from .yarnrc.yml so that it tracks the checkout rather
     * than a version hardcoded here.
     */
    static String yarnPath(String yarnrcYml) {
        var matcher = YARN_PATH.matcher(yarnrcYml);
        if (!matcher.find()) {
            throw new IllegalStateException("No yarnPath in .yarnrc.yml; cannot find the vendored yarn release");
        }
        return matcher.group(1);
    }

    /**
     * The environment for a host XUI process: an allowlist of host variables plus the computed set.
     */
    static Map<String, String> processEnvironment(Map<String, String> host, Map<String, String> computed) {
        var environment = new LinkedHashMap<String, String>();
        for (var key : INHERITED_ENVIRONMENT_KEYS) {
            var value = host.get(key);
            if (value != null) {
                environment.put(key, value);
            }
        }
        environment.putAll(computed);
        return environment;
    }
}
