package uk.gov.hmcts.rse.ccd.lib;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

/**
 * The requests HMC has sent the HMI stand-in, kept in a file, one JSON object a line, so the /hmi page still lists
 * hearings awaiting an answer after the stack restarts, as HMC does.
 */
final class HmiRequestLog {

    record HmiRequest(String method, String hearingId, String body, Instant received) {
    }

    private final Path file;
    private final List<HmiRequest> requests = new CopyOnWriteArrayList<>();
    private final Gson gson = new Gson();

    HmiRequestLog(Path file) {
        this.file = file;
        requests.addAll(read());
    }

    /**
     * The log in RSE_LIB_HMI_DIR, which the Gradle plugin sets to build/cftlib/hmi.
     */
    static HmiRequestLog fromEnvironment(Map<String, String> env) {
        var dir = env.getOrDefault("RSE_LIB_HMI_DIR", Path.of(System.getProperty("java.io.tmpdir"), "cftlib-hmi")
            .toString());
        return new HmiRequestLog(Path.of(dir, "requests.jsonl"));
    }

    List<HmiRequest> all() {
        return requests;
    }

    synchronized void add(HmiRequest request) {
        requests.add(request);
        var line = new LinkedHashMap<String, String>();
        line.put("method", request.method());
        line.put("hearingId", request.hearingId());
        line.put("body", request.body());
        line.put("received", request.received().toString());
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, gson.toJson(line) + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not record the HMI request in " + file, e);
        }
    }

    // A line that can't be read, such as one cut short when the stack stopped, is skipped.
    private List<HmiRequest> read() {
        var read = new ArrayList<HmiRequest>();
        if (!Files.exists(file)) {
            return read;
        }
        try {
            for (var line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, String> value = gson.fromJson(line, Map.class);
                    if (value != null && value.get("method") != null && value.get("received") != null) {
                        read.add(new HmiRequest(value.get("method"), value.get("hearingId"), value.get("body"),
                            Instant.parse(value.get("received"))));
                    }
                } catch (JsonParseException | java.time.format.DateTimeParseException e) {
                    // Skipped.
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the HMI requests in " + file, e);
        }
        return read;
    }
}
