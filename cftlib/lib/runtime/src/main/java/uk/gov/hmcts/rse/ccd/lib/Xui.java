package uk.gov.hmcts.rse.ccd.lib;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.zeroturnaround.exec.ProcessExecutor;

/**
 * Builds and runs an XUI app from a shallow clone using the host's node, with no container runtime.
 *
 * <p>The clone, its node_modules and its build output live in a cache under ~/.cftlib and are
 * reused across boots; see {@link XuiBuildMarker}. An engineer who already has XUI checked out can
 * point cftlib at it with RSE_LIB_XUI_DIR / RSE_LIB_XUI_MO_DIR, in which case cftlib builds and
 * runs that checkout as it stands and never moves its HEAD.
 */
public class Xui implements ManagedDependency {

    private static final long INSTALL_TIMEOUT_MINUTES = 60;
    private static final long START_TIMEOUT_SECONDS = 300;
    private static final long AUTH_TIMEOUT_SECONDS = 300;
    private static final long STOP_TIMEOUT_SECONDS = 10;

    private final XuiApp app;

    private Process process;
    private Thread shutdownHook;

    private Xui(XuiApp app) {
        this.app = app;
    }

    public static Xui manageCases() {
        return new Xui(XuiApp.MANAGE_CASES);
    }

    public static Xui manageOrg() {
        return new Xui(XuiApp.MANAGE_ORG);
    }

    @Override
    public String name() {
        return app.appName();
    }

    @Override
    public void start() throws Exception {
        start(XuiEnvironment.interpolationSource());
    }

    void start(Map<String, String> source) throws Exception {
        var port = app.port(source, XuiEnvironment.Network.HOST);
        if (Ports.isListening(port)) {
            log("already listening on port " + port + ", adopting it");
            return;
        }

        var node = source.getOrDefault("RSE_LIB_NODE", "node");
        var range = app.nodeRange(source);
        var nodeVersion = nodeVersion(node, range);
        NodeVersions.require(name(), range, nodeVersion);

        var checkout = checkout(source);
        requireDeclaredNode(checkout, nodeVersion);
        build(checkout, node, source);

        awaitAuthServer();
        launch(checkout, node, source, port);
    }

    /**
     * Terminates the node process cftlib started, leaving an adopted one alone.
     */
    @Override
    public void stop() {
        var running = process;
        process = null;
        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException e) {
                // Already shutting down, which is when the hook itself calls us.
            }
            shutdownHook = null;
        }
        if (running == null || !running.isAlive()) {
            return;
        }
        running.destroy();
        try {
            if (!running.waitFor(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                running.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running.destroyForcibly();
        }
    }

    private String nodeVersion(String node, String range) {
        try {
            return capture(List.of(node, "--version"));
        } catch (Exception e) {
            throw new IllegalStateException(
                "Could not run '" + node + " --version'. " + name() + " is built with the host's node, so node "
                    + range + " must be installed, or set RSE_LIB_XUI=docker to use the published image.", e);
        }
    }

    /**
     * Re-checks the host's node against the range the checked out revision actually declares, which
     * catches a pin in xui-versions.properties that has drifted from the repository.
     */
    private void requireDeclaredNode(Path checkout, String nodeVersion) throws Exception {
        var packageJson = checkout.resolve("package.json");
        if (!Files.exists(packageJson)) {
            throw new IllegalStateException(packageJson + " does not exist; " + checkout
                + " is not an XUI checkout.");
        }
        var declared = NodeVersions.enginesNode(Files.readString(packageJson));
        if (declared != null) {
            NodeVersions.require(name() + " at " + checkout, declared, nodeVersion);
        }
    }

    private Path checkout(Map<String, String> source) throws Exception {
        var override = app.checkoutOverride(source);
        if (override != null) {
            var dir = Path.of(override).toAbsolutePath().normalize();
            if (!Files.isDirectory(dir)) {
                throw new IllegalStateException(dir + " is not a directory, so " + name()
                    + " cannot be built from it.");
            }
            log("using the checkout at " + dir);
            return dir;
        }

        var dir = cacheRoot(source).resolve(app.slug());
        var url = app.repoUrl(source);
        var ref = app.ref(source);
        if (!Files.isDirectory(dir.resolve(".git"))) {
            log("cloning " + url + " at " + ref + " into " + dir);
            deleteRecursively(dir);
            Files.createDirectories(dir.getParent());
            run(XuiCommands.cloneCommands(url, ref, dir), dir.getParent(), Map.of());
        } else if (!XuiCommands.isCommitSha(ref) || !ref.equalsIgnoreCase(head(dir))) {
            log("fetching " + ref + " into " + dir);
            run(XuiCommands.fetchCommands(url, ref, dir), dir, Map.of());
        }
        return dir;
    }

    private void build(Path checkout, String node, Map<String, String> source) throws Exception {
        var marker = cacheRoot(source).resolve(app.slug() + ".built");
        var commit = head(checkout);
        if (!forceRebuild(source) && XuiBuildMarker.upToDate(marker, checkout, commit)) {
            log("reusing the existing build of " + commit);
            return;
        }

        var yarn = checkout.resolve(XuiCommands.yarnPath(Files.readString(checkout.resolve(".yarnrc.yml"))));
        log("installing dependencies and building; this takes several minutes. Output in " + logFile());
        run(List.of(XuiCommands.yarn(node, yarn, app.installArgs())), checkout, XuiCommands.INSTALL_ENVIRONMENT);
        run(List.of(XuiCommands.yarn(node, yarn, List.of("build"))), checkout, XuiCommands.INSTALL_ENVIRONMENT);
        XuiBuildMarker.write(marker, checkout, commit);
    }

    private void launch(Path checkout, String node, Map<String, String> source, int port) throws Exception {
        var computed = app.environment(source, XuiEnvironment.Network.HOST, port);
        var builder = new ProcessBuilder(XuiCommands.startCommand(node, app))
            .directory(checkout.toFile())
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile()));
        builder.environment().clear();
        builder.environment().putAll(XuiCommands.processEnvironment(System.getenv(), computed));

        process = builder.start();
        shutdownHook = new Thread(this::stop, "cftlib-stop-" + name());
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        log("starting on port " + port + "; logs in " + logFile());
        awaitPort(port);
        watch(process);
    }

    /**
     * Reports a process that dies once it is up, which otherwise leaves a boot that looks
     * complete with nothing serving the port.
     */
    private void watch(Process started) {
        var watchdog = new Thread(() -> {
            try {
                var status = started.waitFor();
                if (started == process) {
                    log("exited with status " + status + ". See " + logFile());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "cftlib-watch-" + name());
        watchdog.setDaemon(true);
        watchdog.start();
    }

    /**
     * XUI exits at boot if idam's discovery document does not answer, so it cannot be launched
     * until the auth server is serving. Returns immediately when auth is not cftlib's simulator.
     */
    private void awaitAuthServer() throws Exception {
        var readiness = new Readiness();
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AUTH_TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            if (readiness.authReady()) {
                return;
            }
            Thread.sleep(250);
        }
        throw new IllegalStateException(name() + " was not started because the auth server did not "
            + "answer within " + AUTH_TIMEOUT_SECONDS + " seconds; XUI exits at boot without it.");
    }

    private void awaitPort(int port) throws Exception {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(START_TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                throw new IllegalStateException(name() + " exited with status " + process.exitValue()
                    + " before listening on port " + port + ". See " + logFile());
            }
            if (Ports.isListening(port)) {
                log("listening on port " + port);
                return;
            }
            Thread.sleep(250);
        }
        throw new IllegalStateException(name() + " did not listen on port " + port + " within "
            + START_TIMEOUT_SECONDS + " seconds. See " + logFile());
    }

    private String head(Path checkout) throws Exception {
        return capture(XuiCommands.headCommand(checkout));
    }

    private String capture(List<String> command) throws Exception {
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output;
        try (var stream = process.getInputStream()) {
            output = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
        if (!process.waitFor(1, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new IllegalStateException("'" + String.join(" ", command) + "' did not finish");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("'" + String.join(" ", command) + "' failed with status "
                + process.exitValue() + ": " + output);
        }
        return output;
    }

    private void run(List<List<String>> commands, Path directory, Map<String, String> environment)
        throws Exception {
        try (OutputStream log = new FileOutputStream(logFile(), true)) {
            for (var command : commands) {
                try {
                    new ProcessExecutor().command(command)
                        .directory(directory.toFile())
                        .environment(environment)
                        .redirectOutput(log)
                        .redirectError(log)
                        .exitValueNormal()
                        .timeout(INSTALL_TIMEOUT_MINUTES, TimeUnit.MINUTES)
                        .execute();
                } catch (Exception e) {
                    throw new IllegalStateException(name() + " failed running '"
                        + String.join(" ", command) + "'; see " + logFile(), e);
                }
            }
        }
    }

    /**
     * Removes a cache directory left behind by an interrupted clone, which git will not clone into.
     */
    private static void deleteRecursively(Path dir) throws Exception {
        if (!Files.exists(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    private static boolean forceRebuild(Map<String, String> source) {
        return source.get("RSE_LIB_XUI_REBUILD") != null
            || source.get("RSE_LIB_CLEAN_BOOT") != null
            || source.get("CI") != null;
    }

    private static Path cacheRoot(Map<String, String> source) {
        var configured = source.get("RSE_LIB_CACHE_DIR");
        var root = configured != null && !configured.isBlank()
            ? Path.of(configured.trim())
            : Path.of(System.getProperty("user.home"), ".cftlib");
        return root.toAbsolutePath().normalize().resolve("xui");
    }

    private File logFile() {
        var folder = System.getenv("RSE_LIB_LOG_FOLDER");
        var directory = folder != null ? new File(folder) : new File(".");
        directory.mkdirs();
        return new File(directory, name() + ".log");
    }

    private void log(String message) {
        System.out.println("**** " + name() + ": " + message);
    }
}
