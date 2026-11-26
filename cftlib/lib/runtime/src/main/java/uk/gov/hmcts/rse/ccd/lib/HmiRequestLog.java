package uk.gov.hmcts.rse.ccd.lib;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import com.google.gson.Gson;

/**
 * The requests HMC has sent the HMI stand-in. With RSE_LIB_HMI_DIR set, as the Gradle plugin sets it, they're also
 * kept in a file there, one JSON object a line, so the /hmi page still lists hearings awaiting an answer after the
 * stack restarts, as HMC does.
 *
 * <p>Each line records which HMC database the hearing is in. When that database is recreated, by a clean boot or
 * by removing its volume, HMC numbers its hearings from the start again, so the lines for the old database's
 * hearings are dropped rather than mixed in with the new ones that share their ids.
 */
final class HmiRequestLog {

    record HmiRequest(String method, String hearingId, String body, Instant received) {
    }

    // Null keeps the requests in memory only.
    private final Path file;
    // Identifies HMC's database, so the requests for another database's hearings can be told apart.
    private final Supplier<String> database;
    private final List<HmiRequest> requests = new CopyOnWriteArrayList<>();
    private final Gson gson = new Gson();
    // Writes the file off the caller's thread: HMI's controller runs on the web server's event loop.
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        var thread = new Thread(runnable, "cftlib hmi request log");
        thread.setDaemon(true);
        return thread;
    });
    private boolean loaded;
    private String loadedDatabase;

    HmiRequestLog(Path file, Supplier<String> database) {
        this.file = file;
        this.database = database;
    }

    /**
     * The log in RSE_LIB_HMI_DIR, which the Gradle plugin sets to build/cftlib/hmi, or in memory without it.
     */
    static HmiRequestLog fromEnvironment(Map<String, String> env, Supplier<String> database) {
        var dir = env.get("RSE_LIB_HMI_DIR");
        return new HmiRequestLog(dir == null || dir.isBlank() ? null : Path.of(dir, "requests.jsonl"), database);
    }

    /**
     * The requests for the current database's hearings, oldest first. The file is read the first time they're asked
     * for, once HMC's database is up.
     */
    List<HmiRequest> all() {
        load();
        return requests;
    }

    /**
     * Record a request. It's written to the file in the background, and a failure to write it is logged: it doesn't
     * stop HMI accepting the request.
     */
    void add(HmiRequest request) {
        requests.add(request);
        if (file != null) {
            writer.execute(() -> append(request));
        }
    }

    /**
     * Wait for the requests added so far to be written.
     */
    void awaitWrites() throws InterruptedException, ExecutionException {
        writer.submit(() -> { }).get();
    }

    private synchronized void append(HmiRequest request) {
        load();
        if (loadedDatabase == null) {
            System.out.println("**** cftlib's HMI could not record the " + request.method() + " for hearing "
                + request.hearingId() + " in " + file + ": HMC's database could not be identified");
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, line(request, loadedDatabase) + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException e) {
            System.out.println("**** cftlib's HMI could not record the " + request.method() + " for hearing "
                + request.hearingId() + " in " + file + ": " + e);
        }
    }

    // Reads the requests for the current database's hearings from the file, before any added since the stack started,
    // and drops the rest from it. Tried again next time if HMC's database or the file can't be read.
    private synchronized void load() {
        if (loaded) {
            return;
        }
        if (file == null) {
            loaded = true;
            return;
        }
        try {
            var current = database.get();
            var kept = new ArrayList<HmiRequest>();
            var keptLines = new ArrayList<String>();
            var lines = Files.exists(file) ? Files.readAllLines(file, StandardCharsets.UTF_8) : List.<String>of();
            for (var line : lines) {
                var request = read(line, current);
                if (request != null) {
                    kept.add(request);
                    keptLines.add(line);
                }
            }
            if (keptLines.size() < lines.size()) {
                var rewritten = file.resolveSibling(file.getFileName() + ".tmp");
                Files.write(rewritten, keptLines, StandardCharsets.UTF_8);
                Files.move(rewritten, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            requests.addAll(0, kept);
            loadedDatabase = current;
            loaded = true;
        } catch (IOException | RuntimeException e) {
            System.out.println("**** cftlib's HMI could not read the hearing requests in " + file + ": " + e);
        }
    }

    private String line(HmiRequest request, String database) {
        var line = new LinkedHashMap<String, String>();
        line.put("database", database);
        line.put("method", request.method());
        line.put("hearingId", request.hearingId());
        line.put("body", request.body());
        line.put("received", request.received().toString());
        return gson.toJson(line);
    }

    // The request on a line, or null for one that's for another database's hearing or can't be read, such as one cut
    // short when the stack stopped.
    private HmiRequest read(String line, String database) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> value = gson.fromJson(line, Map.class);
            if (value == null || !database.equals(value.get("database"))
                || !(value.get("method") instanceof String method)
                || !(value.get("received") instanceof String received)) {
                return null;
            }
            return new HmiRequest(method, text(value.get("hearingId")), text(value.get("body")),
                Instant.parse(received));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String text(Object value) {
        if (value != null && !(value instanceof String)) {
            throw new IllegalArgumentException("Not text: " + value);
        }
        return (String) value;
    }
}
