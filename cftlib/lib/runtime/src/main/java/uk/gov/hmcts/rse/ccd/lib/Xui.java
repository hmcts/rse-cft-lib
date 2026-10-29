package uk.gov.hmcts.rse.ccd.lib;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.stop.ProcessStopper;

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

    /**
     * Stops a build and everything it started. Yarn runs the real work in children of its own,
     * and destroying only the process cftlib launched would leave those writing to the build
     * output while the next boot builds into the same checkout.
     */
    private static final ProcessStopper STOP_WITH_DESCENDANTS = process -> {
        // Collected before the parent goes: the children of a process that has exited are no
        // longer reachable through it.
        var descendants = process.toHandle().descendants().toList();
        process.destroy();
        descendants.forEach(ProcessHandle::destroy);
    };

    private final XuiApp app;

    private volatile Process process;
    private volatile boolean stopped;
    private volatile ProcessHandle adopted;
    private volatile Thread shutdownHook;

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
        var revision = revision(source);
        if (Ports.isListening(port)) {
            // Only a process from one of our own boots, which a hard-killed JVM can leave behind.
            // Anything else is usually a leftover cftlib container, and adopting that would serve
            // the published image rather than the revision pinned for this build.
            var running = ownInstance(source, port);
            if (running != null && revision.equals(running.revision())) {
                log("reusing the instance it left running on port " + port);
                // Adopted, so this boot owns it: otherwise it would hold the port after every
                // later run too, and a switch back to docker mode would fail.
                adopted = ProcessHandle.of(running.pid()).orElse(null);
                return;
            }
            if (running != null) {
                // Adopting it would serve the revision it was built from, however the pin or the
                // checkout has moved on since, and say it was reusing what it left running.
                log("the instance left running on port " + port + " was built from "
                    + running.revision() + ", so it is being replaced with " + revision);
                ProcessHandle.of(running.pid()).ifPresent(stale -> {
                    stale.destroy();
                    awaitExit(stale);
                });
            } else {
                throw new IOException("Port " + port + " is already in use, but not by a " + name()
                    + " cftlib started. A leftover cftlib container is the usual cause, and "
                    + "'docker compose -p cftlib down' clears it. Set " + app.variable()
                    + "=off to run without " + name()
                    + ", or RSE_LIB_DEPENDENCY_MODE=docker to use the published image.");
            }
        }

        var node = source.getOrDefault("RSE_LIB_NODE", "node");
        var range = app.nodeRange(source);
        var nodeVersion = nodeVersion(node, range);
        NodeVersions.require(name(), range, nodeVersion);

        Path checkout;
        // One clone and one build directory are shared by every project on the machine, so two
        // boots starting together would otherwise run yarn install over each other's
        // node_modules. The loser waits and then finds the build it needed already made.
        try (var exclusive = exclusively(source)) {
            checkout = checkout(source);
            requireDeclaredNode(checkout, nodeVersion);
            build(checkout, node, nodeVersion, source);
        }

        awaitAuthServer();
        launch(checkout, node, source, port);
    }

    /**
     * Terminates the node process this boot is using, whether it started it or adopted one left
     * by an earlier boot.
     */
    @Override
    public void stop() {
        // Recorded because stop can arrive while launch is still starting the process, when
        // another dependency has failed: without this the launch would complete unobserved and
        // outlive a JVM that halts without running shutdown hooks.
        stopped = true;
        var reused = adopted;
        adopted = null;
        if (reused != null && reused.isAlive()) {
            reused.destroy();
            // Waited for, because the JVM halts as soon as this returns: a process still
            // holding the port when the next boot probes it is the state adoption exists to
            // prevent.
            awaitExit(reused);
        }
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

    /** Waits out an adopted process, escalating as the one this boot started is escalated. */
    private void awaitExit(ProcessHandle process) {
        try {
            process.onExit().get(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        } catch (ExecutionException | TimeoutException e) {
            process.destroyForcibly();
        }
    }

    private String nodeVersion(String node, String range) {
        try {
            return capture(List.of(node, "--version"));
        } catch (Exception e) {
            throw new IllegalStateException(
                "Could not run '" + node + " --version'. " + name() + " is built with the host's node, so node "
                    + range + " must be installed, or set RSE_LIB_DEPENDENCY_MODE=docker to use the "
                    + "published image.", e);
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
            try {
                NodeVersions.require(name() + " at " + checkout, declared, nodeVersion);
            } catch (IllegalArgumentException e) {
                // This check exists to report a pin that has drifted from the repository, so an
                // engines range it cannot read must not be the thing that stops the boot.
                log("could not read the node range '" + declared + "' declared by " + packageJson
                    + ", so it has not been checked against " + nodeVersion);
            }
        }
    }

    /**
     * Takes the lock that makes cloning and building this app one boot's business at a time.
     * Held in a file, so the operating system releases it even where a boot ends by halting,
     * and taken even when a checkout was supplied: two boots can be pointed at one of those too.
     */
    private Closeable exclusively(Map<String, String> source) throws IOException {
        var file = cacheRoot(source).resolve(app.slug() + ".lock");
        Files.createDirectories(file.getParent());
        var channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        var held = channel.tryLock();
        if (held == null) {
            log("waiting for another cftlib to finish building " + name());
            held = channel.lock();
        }
        var lock = held;
        return () -> {
            try (channel) {
                lock.release();
            }
        };
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
        if (!hasCommit(dir)) {
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

    private void build(Path checkout, String node, String nodeVersion, Map<String, String> source)
        throws Exception {
        var marker = cacheRoot(source).resolve(app.slug() + ".built");
        var commit = head(checkout);
        // A checkout cftlib was pointed at can be edited, so the build has to follow the working
        // tree and not just the commit.
        var workingTree = workingTreeState(checkout);
        // The node that built it matters too: a build made by another version may not run.
        var output = checkout.resolve(app.startArgs().get(app.startArgs().size() - 1));
        if (!forceRebuild(source)
            && XuiBuildMarker.upToDate(marker, checkout, commit, workingTree, nodeVersion, output)) {
            log("reusing the existing build of " + commit);
            return;
        }

        var yarn = checkout.resolve(XuiCommands.yarnPath(Files.readString(checkout.resolve(".yarnrc.yml"))));
        log("installing dependencies and building; this takes several minutes. Output in " + logFile());
        run(List.of(XuiCommands.yarn(node, yarn, app.installArgs())), checkout, XuiCommands.INSTALL_ENVIRONMENT);
        run(List.of(XuiCommands.yarn(node, yarn, List.of("build"))), checkout, XuiCommands.INSTALL_ENVIRONMENT);
        XuiBuildMarker.write(marker, checkout, commit, workingTree, nodeVersion);
    }

    private void launch(Path checkout, String node, Map<String, String> source, int port) throws Exception {
        var computed = app.environment(source, XuiEnvironment.Network.HOST, port);
        var builder = new ProcessBuilder(XuiCommands.startCommand(node, app))
            .directory(checkout.toFile())
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile()));
        builder.environment().clear();
        builder.environment().putAll(XuiCommands.processEnvironment(System.getenv(), computed));

        // Held locally as well, since a concurrent stop() clears the field and everything below
        // would then fail on a null rather than reporting why the boot ended.
        var started = builder.start();
        process = started;
        if (stopped) {
            // Asked to stop while this was starting, so do not leave it behind.
            stop();
            started.destroy();
            throw new IllegalStateException(name() + " was stopped while it was starting");
        }
        recordPid(started, port, source);
        shutdownHook = new Thread(this::stop, "cftlib-stop-" + name());
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        log("starting on port " + port + "; logs in " + logFile());
        awaitPort(started, port);
        watch(started);
    }

    /**
     * Where a boot records the instance it leaves running, by port: the clone is shared by every
     * project on the machine, but the instance is not, and one project running on another port
     * would otherwise overwrite the record of an instance it has nothing to do with.
     */
    private Path pidFile(Map<String, String> source, int port) {
        return cacheRoot(source).resolve(app.slug() + "-" + port + ".pid");
    }

    /** What an instance was built from: a checkout given to cftlib, or the revision it pinned. */
    private String revision(Map<String, String> source) {
        var override = app.checkoutOverride(source);
        return override != null ? override : app.ref(source);
    }

    /** An instance a previous boot recorded, as its pid file describes it. */
    private record Instance(long pid, String revision) {
    }

    /**
     * The instance this boot's own records say is holding the port, or null for anything else.
     * Each field is a line of its own, since a checkout path and a command both hold spaces.
     */
    private Instance ownInstance(Map<String, String> source, int port) {
        var file = pidFile(source, port);
        if (!Files.isReadable(file)) {
            return null;
        }
        try {
            // Split rather than read as lines, which drops the empty last field a process whose
            // command could not be read leaves behind, and with it the tolerance below.
            var lines = Files.readString(file).split("\n", -1);
            if (lines.length < 4) {
                // Written by a cftlib that recorded less than this one does.
                return null;
            }
            var pid = Long.parseLong(lines[0].trim());
            if (Integer.parseInt(lines[1].trim()) != port) {
                return null;
            }
            var command = lines[3];
            var alive = ProcessHandle.of(pid)
                .filter(ProcessHandle::isAlive)
                // A pid outlives the process that wrote it, so the command has to match too:
                // adopting on the pid alone could serve a leftover container's published image
                // in place of the revision this boot pinned. Where the command could not be read
                // at launch there is nothing to compare, and refusing would leave a process
                // cftlib started unadoptable, with advice about containers that cannot help.
                .filter(process -> command.isBlank()
                    || process.info().command().filter(command::equals).isPresent())
                .isPresent();
            return alive ? new Instance(pid, lines[2]) : null;
        } catch (IOException | NumberFormatException e) {
            return null;
        }
    }

    private void recordPid(Process started, int port, Map<String, String> source) throws IOException {
        var file = pidFile(source, port);
        Files.createDirectories(file.getParent());
        // The revision and the command as well as the pid: a pid outlives its process, so
        // adopting on the pid alone could serve something else that happens to hold the port,
        // and adopting without the revision could serve a build of the wrong one.
        Files.writeString(file, String.join("\n",
            Long.toString(started.pid()),
            Integer.toString(port),
            revision(source),
            started.info().command().orElse("")));
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

    private void awaitPort(Process started, int port) throws Exception {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(START_TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            if (!started.isAlive()) {
                throw new IllegalStateException(name() + " exited with status " + started.exitValue()
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

    /**
     * A digest of the checkout's uncommitted changes, so that editing a checkout cftlib was
     * pointed at invalidates its build while a stable working tree keeps reusing it.
     */
    private String workingTreeState(Path checkout) throws Exception {
        // The diff rather than the status: a status lists the paths that changed, so editing an
        // already modified file again would leave it identical and the stale build in place.
        var tracked = capture(List.of("git", "-C", checkout.toString(), "diff", "HEAD"));
        // NUL separated, because git quotes a path with unusual characters in it and two
        // different such paths can quote to the same text.
        var untracked = capture(List.of("git", "-C", checkout.toString(),
            "ls-files", "--others", "--exclude-standard", "-z"));
        var digest = new StringBuilder(tracked);
        for (var path : untracked.split("\0")) {
            if (!path.isBlank()) {
                var file = checkout.resolve(path.trim());
                digest.append(path).append(':')
                    .append(Files.isReadable(file) ? Files.size(file) + "@" + Files.getLastModifiedTime(file) : "?");
            }
        }
        return digest.isEmpty() ? "clean"
            : Integer.toHexString(digest.toString().hashCode()) + ":" + digest.length();
    }

    private String head(Path checkout) throws Exception {
        return capture(XuiCommands.headCommand(checkout));
    }

    /**
     * Whether a cached clone holds a commit. A clone of a pinned sha starts with a git init, so
     * one interrupted during its fetch — which takes minutes the first time — leaves a .git
     * behind with no commit in it. Fetching into that fails, and would go on failing every boot
     * until the directory was removed by hand, so it is cloned again instead.
     *
     * <p>Only an absent commit is answered for. Anything else git has to say about the
     * repository is raised, rather than taken as a reason to delete a clone and its
     * node_modules: a devcontainer that git considers a dubiously owned checkout would
     * otherwise have every boot spend minutes cloning it afresh.
     */
    private boolean hasCommit(Path dir) throws Exception {
        if (!Files.isDirectory(dir.resolve(".git"))) {
            return false;
        }
        // Exit 1 and nothing on stdout is how it says there is no commit.
        if (!capture(XuiCommands.headIfAnyCommand(dir), Set.of(0, 1)).isBlank()) {
            return true;
        }
        log("the clone in " + dir + " holds no commit, so it is being made again");
        return false;
    }

    private String capture(List<String> command) throws Exception {
        return capture(command, Set.of(0));
    }

    /**
     * Runs a command that answers with its exit status as well as its output, so that a status
     * the caller expects is read as an answer rather than raised as a failure.
     */
    private String capture(List<String> command, Set<Integer> expected) throws Exception {
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output;
        try (var stream = process.getInputStream()) {
            output = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
        if (!process.waitFor(1, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new IllegalStateException("'" + String.join(" ", command) + "' did not finish");
        }
        if (!expected.contains(process.exitValue())) {
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
                        // An install interrupted because another dependency failed must take its
                        // child with it, or it keeps building after the JVM has gone.
                        .stopper(STOP_WITH_DESCENDANTS)
                        .destroyOnExit()
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
