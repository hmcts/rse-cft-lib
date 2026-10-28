package uk.gov.hmcts.libconsumer;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import uk.gov.hmcts.rse.ccd.lib.Database;
import uk.gov.hmcts.rse.ccd.lib.test.CftlibTest;

/**
 * Work allocation with the wa feature: a CCD case event reaches wa-case-event-handler, whose initiation DMN starts
 * WA's task process in Camunda, through wa-workflow-api.
 */
// The same context as LibConsumerApplicationTests, which Spring reuses rather than starting another on the same port.
// The feature runs containers, so is off when the test-project runs without docker.
@EnabledIfEnvironmentVariable(named = "RSE_LIB_FEATURES", matches = "(.*,)?wa(,.*)?")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkAllocationTests extends CftlibTest {

    private static final String ENGINE = "http://localhost:8999/engine-rest";

    private final HttpClient client = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    @Test
    @SneakyThrows
    void createsATaskForACaseEvent() {
        cftlib().importCamundaDefinitions("divorce",
            new File("src/cftlibTest/resources/wa/wa-task-initiation-divorce-nfd.dmn"),
            new File("src/cftlibTest/resources/wa/wa-task-cancellation-divorce-nfd.dmn"));
        var caseId = String.valueOf(ThreadLocalRandom.current().nextLong(1_000_000_000_000_000L, 1_999_999_999_999_999L));

        // The row data store writes for an event whose definition has Publish set.
        try (var c = cftlib().getConnection(Database.Datastore)) {
            var insert = c.prepareStatement("insert into message_queue_candidates (id, message_type, message_information)"
                + " values (nextval('message_queue_candidates_id_seq'), 'CASE_EVENT', ?::jsonb)");
            insert.setString(1, gson.toJson(Map.of(
                "EventInstanceId", UUID.randomUUID().toString(),
                "EventTimeStamp", LocalDateTime.now().toString(),
                "CaseId", caseId,
                "JurisdictionId", "DIVORCE",
                "CaseTypeId", "NFD",
                "EventId", "cftlib-wa-event",
                "PreviousStateId", "Submitted",
                "NewStateId", "Submitted",
                "UserId", "cftlib-test"
            )));
            insert.execute();
        }

        cftlib().deliverCaseEvents();

        // wa-workflow-api completes the process's idempotency check before Camunda creates the task.
        var tasks = await().atMost(Duration.ofMinutes(1)).pollInterval(Duration.ofSeconds(1))
            .until(() -> get("/task?processVariables=" + URLEncoder.encode("caseId_eq_" + caseId,
                StandardCharsets.UTF_8)), t -> t.size() == 1);
        assertThat(tasks.asList(), hasSize(1));
        assertThat(tasks.get(0).getAsJsonObject().get("name").getAsString(), equalTo("Review the case"));
    }

    @SneakyThrows
    private JsonArray get(String path) {
        var response = client.send(HttpRequest.newBuilder(URI.create(ENGINE + path)).build(),
            HttpResponse.BodyHandlers.ofString());
        return gson.fromJson(response.body(), JsonArray.class);
    }
}
