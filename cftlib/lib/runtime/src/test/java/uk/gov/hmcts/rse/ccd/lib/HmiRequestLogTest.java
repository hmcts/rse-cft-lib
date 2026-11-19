package uk.gov.hmcts.rse.ccd.lib;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HmiRequestLogTest {

    @TempDir
    Path dir;

    @Test
    void keepsRequestsAcrossRestarts() {
        var file = dir.resolve("hmi/requests.jsonl");
        var received = Instant.parse("2026-11-19T10:20:53Z");
        var log = new HmiRequestLog(file);
        log.add(new HmiRequestLog.HmiRequest("POST", "2000000044", "{\"hearingRequest\": {}}", received));
        log.add(new HmiRequestLog.HmiRequest("DELETE", "2000000044", null, received.plusSeconds(60)));

        var afterRestart = new HmiRequestLog(file);

        assertThat(afterRestart.all()).containsExactly(
            new HmiRequestLog.HmiRequest("POST", "2000000044", "{\"hearingRequest\": {}}", received),
            new HmiRequestLog.HmiRequest("DELETE", "2000000044", null, received.plusSeconds(60)));
    }

    @Test
    void skipsALineCutShort() throws Exception {
        var file = dir.resolve("requests.jsonl");
        Files.writeString(file, "{\"method\":\"POST\",\"hearingId\":\"1\",\"body\":\"{}\",\"received\":"
            + "\"2026-11-19T10:20:53Z\"}\n{\"method\":\"PU");

        assertThat(new HmiRequestLog(file).all()).extracting(HmiRequestLog.HmiRequest::hearingId).containsExactly("1");
    }

    @Test
    void startsEmptyWithoutAFile() {
        assertThat(HmiRequestLog.fromEnvironment(Map.of("RSE_LIB_HMI_DIR", dir.toString())).all()).isEmpty();
    }
}
