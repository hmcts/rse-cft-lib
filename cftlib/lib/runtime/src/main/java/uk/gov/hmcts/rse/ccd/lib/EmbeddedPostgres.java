package uk.gov.hmcts.rse.ccd.lib;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Optional;

import lombok.SneakyThrows;

/**
 * Runs postgres from the binaries published by io.zonky.test, with no container runtime.
 */
public class EmbeddedPostgres implements ManagedDependency {

    private static final Duration STARTUP_WAIT = Duration.ofSeconds(60);
    private static final Duration SHUTDOWN_WAIT = Duration.ofSeconds(20);
    private static final int LOG_TAIL_LINES = 40;

    private final Path dataDirectory;
    private final Path logFile;
    private final int port;
    private final boolean cleanBoot;
    private final boolean leaveRunning;

    private io.zonky.test.db.postgres.embedded.EmbeddedPostgres instance;

    public EmbeddedPostgres() {
        this(
            buildDirectory(System.getenv("RSE_LIB_LOG_FOLDER")).resolve("postgres"),
            logFolder(System.getenv("RSE_LIB_LOG_FOLDER")).resolve("postgres.log"),
            CftlibConfig.dbPort(),
            System.getenv("RSE_LIB_CLEAN_BOOT") != null,
            System.getenv("RSE_LIB_CLEAN_BOOT") == null && System.getenv("CI") == null);
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
        if (cleanBoot) {
            stopPreviousRun();
            deleteDataDirectory();
        }

        if (Ports.isListening(CftlibConfig.dbHost(), port)) {
            System.out.println("**** Cftlib is using the postgres already listening on port " + port);
            return;
        }

        Files.createDirectories(logFile.getParent());
        try {
            instance = io.zonky.test.db.postgres.embedded.EmbeddedPostgres.builder()
                .setPort(port)
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
    }

    @Override
    @SneakyThrows
    public void stop() {
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
    private void stopPreviousRun() {
        var process = postmasterPid()
            .flatMap(ProcessHandle::of)
            .filter(ProcessHandle::isAlive);
        if (process.isEmpty()) {
            return;
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
            System.out.println("**** Cftlib could not free port " + port + ", so the database already "
                + "listening there will be used as it is rather than being recreated");
        }
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
        var deadline = System.nanoTime() + SHUTDOWN_WAIT.toNanos();
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
