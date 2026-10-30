package uk.gov.hmcts.rse.ccd.lib;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import lombok.SneakyThrows;

/**
 * Runs postgres from the binaries published by io.zonky.test, with no container runtime.
 */
public class EmbeddedPostgres implements ManagedDependency {

    private static final Duration STARTUP_WAIT = Duration.ofSeconds(60);
    private static final Duration SHUTDOWN_WAIT = Duration.ofSeconds(20);
    /** All a smart shutdown gets, since it cannot finish while this JVM holds the pools open. */
    private static final Duration SMART_SHUTDOWN_GRACE = Duration.ofSeconds(2);
    private static final int LOG_TAIL_LINES = 40;

    private final Path dataDirectory;
    private final Path logFile;
    private final int port;
    private final boolean cleanBoot;
    private final boolean leaveRunning;

    private volatile io.zonky.test.db.postgres.embedded.EmbeddedPostgres instance;
    private volatile boolean stopped;
    private volatile ProcessHandle adopted;

    public EmbeddedPostgres() {
        this(CftlibConfig.dbPort());
    }

    /**
     * The server is shut down with the JVM rather than left running as the container is. The data
     * directory persists either way, so a restart costs a second, whereas a server outliving the
     * JVM holds its port against a switch back to docker and has no 'compose down' to reclaim it.
     */
    private EmbeddedPostgres(int port) {
        this(
            // Per port, since bootWithCCD and cftlibTest are commonly configured with different
            // ports and a cluster left running on one would hold the other's data directory.
            buildDirectory(System.getenv("RSE_LIB_LOG_FOLDER")).resolve("postgres").resolve(
                String.valueOf(port)),
            logFolder(System.getenv("RSE_LIB_LOG_FOLDER")).resolve("postgres.log"),
            port,
            // CI counts as a clean boot, as it does for the containers and for the XUI build: a
            // reused agent workspace would otherwise carry the last build's databases into this one.
            System.getenv("RSE_LIB_CLEAN_BOOT") != null || System.getenv("CI") != null,
            false);
    }

    EmbeddedPostgres(Path dataDirectory, Path logFile, int port, boolean cleanBoot, boolean leaveRunning) {
        this.dataDirectory = dataDirectory;
        this.logFile = logFile;
        this.port = port;
        this.cleanBoot = cleanBoot;
        this.leaveRunning = leaveRunning;
    }

    /**
     * The cftlib build folder of the consuming project, derived from the log folder the gradle
     * plugin sets, since the runtime's working directory is not guaranteed to be the project.
     */
    static Path buildDirectory(String logFolder) {
        if (logFolder != null && !logFolder.isBlank()) {
            var parent = Path.of(logFolder).toAbsolutePath().normalize().getParent();
            if (parent != null) {
                return parent;
            }
        }
        return Path.of("build", "cftlib").toAbsolutePath().normalize();
    }

    static Path logFolder(String logFolder) {
        return logFolder != null && !logFolder.isBlank()
            ? Path.of(logFolder).toAbsolutePath().normalize()
            : buildDirectory(logFolder).resolve("logs");
    }

    @Override
    public String name() {
        return "postgres";
    }

    @Override
    public void start() throws IOException {
        // Nothing is signalled or deleted until something is known to be serving the port: a
        // pid file outlives its server, and a boot that cannot have the port is going to fail
        // without taking the databases of previous boots with it.
        if (Ports.isListening(CftlibConfig.dbHost(), port)) {
            // Only a server from one of our own previous boots. Anything else on the port is most
            // often a leftover cftlib container, which would quietly put docker back in the mix.
            if (!ownPostgresIsRunning()) {
                throw new IOException("Port " + port + " is already in use, but not by a postgres cftlib "
                    + "started: its data directory " + dataDirectory + " holds no matching postmaster. "
                    + "A leftover cftlib container is the usual cause, and 'docker rm -f "
                    + "cftlib-shared-database-pg-1' clears it. Set RSE_LIB_DB_PORT to use a different "
                    + "port, or RSE_LIB_DB=external to have cftlib use the database already there.");
            }
            if (!cleanBoot || !stopPreviousRun()) {
                System.out.println("**** Cftlib is using the postgres it left running on port " + port);
                // Adopted, so this boot owns it from here: without that it would hold the port
                // after every subsequent run too, and a switch back to docker mode would fail.
                adopted = postmasterPid().flatMap(ProcessHandle::of).orElse(null);
                return;
            }
        }

        if (cleanBoot) {
            deleteDataDirectory();
        }

        Files.createDirectories(logFile.getParent());
        try {
            instance = io.zonky.test.db.postgres.embedded.EmbeddedPostgres.builder()
                .setPort(port)
                // Spelt out, because initdb otherwise takes all of it from the environment's
                // locale and the data directory then freezes that choice for every later boot.
                // Under a plain C locale it picks SQL_ASCII, where a multi-byte character is a
                // string of separate bytes and accented case data folds differently from the
                // same case on a UTF-8 machine. The collation decides what a text comparison
                // answers — whether a stored 'ashley' is inside a bound of 'Z' — and a search
                // that depends on the developer's locale for which cases come back is worse than
                // one that sorts by byte everywhere. C is the only name every platform has.
                .setLocaleConfig("encoding", "UTF8")
                .setLocaleConfig("lc-collate", "C")
                .setLocaleConfig("lc-ctype", "C")
                .setDataDirectory(dataDirectory.toFile())
                .setCleanDataDirectory(false)
                .setRegisterShutdownHook(!leaveRunning)
                .setPGStartupWait(STARTUP_WAIT)
                .setPgBinaryResolver(PostgresBinaries.INSTANCE)
                .setOverrideWorkingDirectory(PostgresBinaries.extractionDirectory().toFile())
                .setOutputRedirector(ProcessBuilder.Redirect.appendTo(logFile.toFile()))
                .start();
        } catch (IOException | RuntimeException | ExceptionInInitializerError e) {
            throw new IOException(failureMessage(e), e);
        } finally {
            PostgresBinaries.restrictExtractionDirectory();
        }
        if (stopped) {
            // Asked to stop while this was starting, so do not leave it behind.
            stop();
            throw new IOException("Postgres was stopped while it was starting");
        }
    }

    @Override
    @SneakyThrows
    public void stop() {
        stopped = true;
        var reused = adopted;
        adopted = null;
        if (reused != null && reused.isAlive()) {
            // A postmaster reads SIGTERM as a smart shutdown and waits for every client to
            // disconnect. The CFT apps hold their pools open until this JVM halts, which is the
            // next thing that happens, so the signal is given a moment to work and the server is
            // then killed. Postgres recovers from that as it starts, which is what the previous
            // boot's own hard exit left it to do anyway.
            reused.destroy();
            if (!waitForExit(reused, SMART_SHUTDOWN_GRACE)) {
                reused.destroyForcibly();
                waitForExit(reused);
            }
        }
        if (instance == null) {
            return;
        }
        if (leaveRunning) {
            System.out.println("**** Cftlib is leaving postgres running on port " + port
                + " so the next boot can reuse it." + stopHint());
            return;
        }
        instance.close();
    }

    private String stopHint() {
        return postmasterPid()
            .map(pid -> " Stop it with: kill " + pid)
            .orElse("");
    }

    /**
     * Shuts down the postgres a previous boot deliberately left running, so that a clean boot can
     * delete its data directory.
     */
    private boolean stopPreviousRun() {
        var process = postmasterPid()
            .flatMap(ProcessHandle::of)
            .filter(ProcessHandle::isAlive)
            // Checked before signalling, not only before adopting: a pid in a stale file can
            // belong to anything by now, and this path sends SIGTERM and then SIGKILL.
            .filter(this::isOurPostgres);
        if (process.isEmpty()) {
            return true;
        }

        var postmaster = process.get();
        System.out.println("**** Cftlib is stopping the postgres from the previous boot (pid "
            + postmaster.pid() + ") for a clean boot");
        postmaster.destroy();
        if (!waitForExit(postmaster)) {
            postmaster.destroyForcibly();
            waitForExit(postmaster);
        }

        if (Ports.isListening(CftlibConfig.dbHost(), port)) {
            // Its data directory must be left alone: it belongs to the server still serving it.
            System.out.println("**** Cftlib could not free port " + port + ", so the database already "
                + "listening there will be used as it is rather than being recreated");
            return false;
        }
        return true;
    }

    private boolean ownPostgresIsRunning() {
        return postmasterPid()
            .flatMap(ProcessHandle::of)
            .filter(ProcessHandle::isAlive)
            // A pid outlives the process that wrote it, and after a reboot it can belong to
            // something else entirely, so the process has to look like the server we left.
            .filter(this::isOurPostgres)
            .isPresent();
    }

    /**
     * Whether a process is the server for this data directory, rather than merely a postgres: the
     * host's own server is a postgres too, and a recorded pid can have been reassigned to it.
     */
    private boolean isOurPostgres(ProcessHandle process) {
        if (!isPostgres(process)) {
            return false;
        }
        var arguments = process.info().arguments();
        if (arguments.isEmpty()) {
            // Not readable for another user's process, and on some platforms not at all, so this
            // falls back to the weaker check rather than refusing to recognise our own server.
            return true;
        }
        return List.of(arguments.get()).contains(dataDirectory.toString());
    }

    private static boolean isPostgres(ProcessHandle process) {
        // The suffix differs by platform: zonky publishes binaries for windows too.
        return process.info().command()
            .map(command -> command.endsWith(".exe") ? command.substring(0, command.length() - 4) : command)
            .filter(command -> command.endsWith("postgres"))
            .isPresent();
    }

    private Optional<Long> postmasterPid() {
        var pidFile = dataDirectory.resolve("postmaster.pid");
        if (!Files.isReadable(pidFile)) {
            return Optional.empty();
        }
        try {
            // postmaster.pid carries the pid on its first line and the port it serves on its fourth.
            var lines = Files.readAllLines(pidFile);
            if (lines.size() < 4 || !lines.get(3).trim().equals(String.valueOf(port))) {
                return Optional.empty();
            }
            return Optional.of(Long.parseLong(lines.get(0).trim()));
        } catch (IOException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    private void deleteDataDirectory() throws IOException {
        if (!Files.exists(dataDirectory)) {
            return;
        }
        try (var paths = Files.walk(dataDirectory)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private String failureMessage(Throwable failure) {
        var cause = failure instanceof ExceptionInInitializerError && failure.getCause() != null
            ? failure.getCause()
            : failure;
        return "Postgres failed to start on port " + port + ": " + cause.getMessage()
            + System.lineSeparator()
            + "Its data directory is " + dataDirectory + " and its output is in " + logFile + "."
            + System.lineSeparator()
            + logTail()
            + "Re-run with RSE_LIB_CLEAN_BOOT=true to recreate the data directory, or run the "
            + "dependencies in docker with RSE_LIB_DEPENDENCY_MODE=docker.";
    }

    private String logTail() {
        var tail = new ArrayDeque<String>();
        try (var lines = Files.lines(logFile)) {
            lines.forEach(line -> {
                tail.addLast(line);
                if (tail.size() > LOG_TAIL_LINES) {
                    tail.removeFirst();
                }
            });
        } catch (IOException | UncheckedIOException e) {
            return "";
        }
        return tail.isEmpty()
            ? ""
            : String.join(System.lineSeparator(), tail) + System.lineSeparator();
    }

    private static boolean waitForExit(ProcessHandle process) {
        return waitForExit(process, SHUTDOWN_WAIT);
    }

    private static boolean waitForExit(ProcessHandle process, Duration wait) {
        var deadline = System.nanoTime() + wait.toNanos();
        while (process.isAlive() && System.nanoTime() < deadline) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return !process.isAlive();
    }
}
