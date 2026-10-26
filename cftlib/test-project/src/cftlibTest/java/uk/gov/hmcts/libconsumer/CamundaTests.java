package uk.gov.hmcts.libconsumer;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.postgresql.Driver;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import uk.gov.hmcts.rse.ccd.lib.test.CftlibTest;

/**
 * The Camunda engine cftlib runs when a build sets RSE_LIB_COMPOSE_PROFILES=camunda on the task, as this project's
 * cftlibTest task does.
 */
// The same context as LibConsumerApplicationTests, which Spring reuses rather than starting another on the same port.
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CamundaTests extends CftlibTest {

    private static final String ENGINE = "http://localhost:8999/engine-rest";
    private static final String PROCESS_KEY = "cftlibTest";
    private static final String TOPIC = "cftlib-test";

    // One external task between start and end, so the test plays the part of a service's worker.
    private static final String BPMN = """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                          xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                          id="definitions" targetNamespace="http://bpmn.io/schema/bpmn">
          <bpmn:process id="%s" isExecutable="true" camunda:historyTimeToLive="1">
            <bpmn:startEvent id="start"/>
            <bpmn:sequenceFlow id="toTask" sourceRef="start" targetRef="task"/>
            <bpmn:serviceTask id="task" camunda:type="external" camunda:topic="%s"/>
            <bpmn:sequenceFlow id="toEnd" sourceRef="task" targetRef="end"/>
            <bpmn:endEvent id="end"/>
          </bpmn:process>
        </bpmn:definitions>
        """.formatted(PROCESS_KEY, TOPIC);

    private final HttpClient client = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    @Test
    @SneakyThrows
    void createsCamundasDatabase() {
        var port = System.getenv().getOrDefault("RSE_LIB_DB_PORT", "6432");
        var credentials = new Properties();
        credentials.setProperty("user", "postgres");
        credentials.setProperty("password", "postgres");
        // The driver directly: DriverManager does not see drivers on cftlib's isolated test classloader.
        try (var connection = new Driver().connect("jdbc:postgresql://localhost:" + port + "/postgres", credentials);
             var statement = connection.prepareStatement("select 1 from pg_database where datname = 'camunda'")) {
            assertThat(statement.executeQuery().next(), equalTo(true));
        }
    }

    @Test
    @SneakyThrows
    void runsABusinessProcessWithAnExternalTask() {
        await().atMost(Duration.ofMinutes(3)).pollInterval(Duration.ofSeconds(2))
            .ignoreExceptions()
            .until(() -> get("/engine").statusCode() == 200);

        assertThat(deploy().statusCode(), equalTo(200));
        var instance = post("/process-definition/key/" + PROCESS_KEY + "/start", Map.of());
        assertThat(instance.statusCode(), equalTo(200));
        var instanceId = gson.fromJson(instance.body(), JsonObject.class).get("id").getAsString();

        var locked = post("/external-task/fetchAndLock", Map.of(
            "workerId", "cftlib-test",
            "maxTasks", 1,
            "topics", new Object[] {Map.of("topicName", TOPIC, "lockDuration", 60_000)}
        ));
        var tasks = gson.fromJson(locked.body(), JsonArray.class);
        assertThat(tasks.asList(), hasSize(1));
        var taskId = tasks.get(0).getAsJsonObject().get("id").getAsString();
        assertThat(post("/external-task/" + taskId + "/complete", Map.of("workerId", "cftlib-test")).statusCode(),
            equalTo(204));

        var history = gson.fromJson(get("/history/process-instance/" + instanceId).body(), JsonObject.class);
        assertThat(history.get("state").getAsString(), equalTo("COMPLETED"));
    }

    @SneakyThrows
    private HttpResponse<String> deploy() {
        var boundary = UUID.randomUUID().toString();
        var body = "--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"deployment-name\"\r\n\r\ncftlib-test\r\n"
            + "--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"file\"; filename=\"cftlib-test.bpmn\"\r\n"
            + "Content-Type: application/xml\r\n\r\n" + BPMN + "\r\n"
            + "--" + boundary + "--\r\n";
        return client.send(HttpRequest.newBuilder(URI.create(ENGINE + "/deployment/create"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    }

    @SneakyThrows
    private HttpResponse<String> get(String path) {
        return client.send(HttpRequest.newBuilder(URI.create(ENGINE + path)).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    @SneakyThrows
    private HttpResponse<String> post(String path, Object json) {
        return client.send(HttpRequest.newBuilder(URI.create(ENGINE + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(json)))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    }
}
