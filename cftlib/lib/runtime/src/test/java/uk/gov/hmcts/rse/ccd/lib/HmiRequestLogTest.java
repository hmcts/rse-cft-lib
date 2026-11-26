package uk.gov.hmcts.rse.ccd.lib;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HmiRequestLogTest {

    private static final Instant RECEIVED = Instant.parse("2026-11-19T10:20:53Z");

    @TempDir
    Path dir;

    @Test
    void keepsRequestsAcrossRestarts() throws Exception {
        var file = dir.resolve("hmi/requests.jsonl");
        var log = new HmiRequestLog(file, () -> "db1");
        log.add(new HmiRequestLog.HmiRequest("POST", "2000000044", "{\"hearingRequest\": {}}", RECEIVED));
        log.add(new HmiRequestLog.HmiRequest("DELETE", "2000000044", null, RECEIVED.plusSeconds(60)));
        log.awaitWrites();

        var afterRestart = new HmiRequestLog(file, () -> "db1");

        assertThat(afterRestart.all()).containsExactly(
            new HmiRequestLog.HmiRequest("POST", "2000000044", "{\"hearingRequest\": {}}", RECEIVED),
            new HmiRequestLog.HmiRequest("DELETE", "2000000044", null, RECEIVED.plusSeconds(60)));
    }

    @Test
    void listsRequestsFromBeforeARestartFirst() throws Exception {
        var file = dir.resolve("requests.jsonl");
        var before = new HmiRequestLog(file, () -> "db1");
        before.add(new HmiRequestLog.HmiRequest("POST", "1", "{}", RECEIVED));
        before.awaitWrites();

        var after = new HmiRequestLog(file, () -> "db1");
        after.add(new HmiRequestLog.HmiRequest("PUT", "1", "{}", RECEIVED.plusSeconds(60)));
        after.awaitWrites();

        assertThat(after.all()).extracting(HmiRequestLog.HmiRequest::method).containsExactly("POST", "PUT");
        assertThat(new HmiRequestLog(file, () -> "db1").all()).extracting(HmiRequestLog.HmiRequest::method)
            .containsExactly("POST", "PUT");
    }

    // A clean boot recreates HMC's database, which numbers its hearings from the start again.
    @Test
    void dropsTheRequestsForAnotherDatabasesHearings() throws Exception {
        var file = dir.resolve("requests.jsonl");
        var before = new HmiRequestLog(file, () -> "db1");
        before.add(new HmiRequestLog.HmiRequest("POST", "2000000001", "{}", RECEIVED));
        before.add(new HmiRequestLog.HmiRequest("DELETE", "2000000001", null, RECEIVED.plusSeconds(60)));
        before.awaitWrites();

        var afterCleanBoot = new HmiRequestLog(file, () -> "db2");
        afterCleanBoot.add(new HmiRequestLog.HmiRequest("POST", "2000000001", "{}", RECEIVED.plusSeconds(120)));
        afterCleanBoot.awaitWrites();

        assertThat(afterCleanBoot.all()).containsExactly(
            new HmiRequestLog.HmiRequest("POST", "2000000001", "{}", RECEIVED.plusSeconds(120)));
        assertThat(Files.readAllLines(file)).hasSize(1);
    }

    @Test
    void skipsLinesItCannotRead() throws Exception {
        var file = dir.resolve("requests.jsonl");
        Files.writeString(file, String.join("\n",
            "{\"database\":\"db1\",\"method\":\"POST\",\"hearingId\":\"1\",\"body\":\"{}\",\"received\":"
                + "\"2026-11-19T10:20:53Z\"}",
            "{\"database\":\"db1\",\"method\":\"POST\",\"hearingId\":2,\"body\":\"{}\",\"received\":"
                + "\"2026-11-19T10:20:53Z\"}",
            "{\"database\":\"db1\",\"method\":\"POST\",\"hearingId\":\"3\",\"body\":\"{}\",\"received\":\"soon\"}",
            "[1, 2]",
            "{\"database\":\"db1\",\"method\":\"PU"));

        assertThat(new HmiRequestLog(file, () -> "db1").all()).extracting(HmiRequestLog.HmiRequest::hearingId)
            .containsExactly("1");
    }

    @Test
    void readsNothingUntilHmcsDatabaseIsUp() {
        var log = new HmiRequestLog(dir.resolve("requests.jsonl"), () -> {
            throw new IllegalStateException("HMC's database is not up");
        });
        log.add(new HmiRequestLog.HmiRequest("POST", "1", "{}", RECEIVED));

        assertThat(log.all()).extracting(HmiRequestLog.HmiRequest::hearingId).containsExactly("1");
    }

    @Test
    void stillAcceptsARequestItCannotWrite() throws Exception {
        // The log's directory is a file, so the log can't be written.
        var notADirectory = dir.resolve("hmi");
        Files.writeString(notADirectory, "");
        var log = new HmiRequestLog(notADirectory.resolve("requests.jsonl"), () -> "db1");

        log.add(new HmiRequestLog.HmiRequest("POST", "1", "{}", RECEIVED));
        log.awaitWrites();

        assertThat(log.all()).extracting(HmiRequestLog.HmiRequest::hearingId).containsExactly("1");
    }

    @Test
    void startsEmptyWithoutAFile() {
        assertThat(HmiRequestLog.fromEnvironment(Map.of("RSE_LIB_HMI_DIR", dir.toString()), () -> "db1").all())
            .isEmpty();
    }

    @Test
    void keepsRequestsInMemoryWithoutADirectory() throws Exception {
        var log = HmiRequestLog.fromEnvironment(Map.of(), () -> {
            throw new AssertionError("A log in memory doesn't need HMC's database");
        });
        log.add(new HmiRequestLog.HmiRequest("POST", "1", "{}", RECEIVED));
        log.awaitWrites();

        assertThat(log.all()).extracting(HmiRequestLog.HmiRequest::hearingId).containsExactly("1");
    }
}
