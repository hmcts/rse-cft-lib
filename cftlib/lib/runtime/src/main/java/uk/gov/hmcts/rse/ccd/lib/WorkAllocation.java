package uk.gov.hmcts.rse.ccd.lib;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import lombok.SneakyThrows;
import org.awaitility.Awaitility;

/**
 * Work allocation with the wa feature.
 *
 * <p>In an environment ccd-message-publisher sends CCD's case events to Service Bus, where wa-case-event-handler
 * receives them. Both CCD publishers use JMS, which the Service Bus emulator does not support, so cftlib instead
 * posts each new case event to wa-case-event-handler's /messages, which handles it straight away. Events come from
 * data store's message_queue_candidates table and from any decentralised service's copy of it, listed in
 * RSE_LIB_WA_CASE_EVENT_SOURCES as database:schema pairs.
 */
public class WorkAllocation {
    static final String SYSTEM_USERNAME = "wa-system-user@cftlib.local";
    // The ports Service.waCaseEventHandler and Service.waTaskMonitor pin.
    static final String CASE_EVENT_HANDLER_URL = "http://localhost:8088";
    static final String TASK_MONITOR_URL = "http://localhost:8077";
    static final String DEFAULT_SOURCE = "datastore:public";

    private static volatile WorkAllocation instance;

    private final CFTLibApiImpl api;
    private final HttpClient http = HttpClient.newHttpClient();
    private final Gson gson = new Gson();
    // The newest event in each source from before this run started. Those events, a previous run's, are not
    // delivered.
    private final Map<Source, Long> delivered = new LinkedHashMap<>();
    private static final int MAX_DELIVERY_ATTEMPTS = 5;
    // Failed attempts to deliver each event, by source and id.
    private final Map<String, Integer> attempts = new HashMap<>();
    private LocalDateTime started;

    record Source(String database, String schema) {
    }

    // database:schema pairs, the schema defaulting to public.
    static List<Source> parseSources(String value) {
        return Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(s -> {
                var parts = s.split(":");
                return new Source(parts[0], parts.length > 1 ? parts[1] : "public");
            })
            .toList();
    }

    WorkAllocation(CFTLibApiImpl api, List<Source> sources) {
        this.api = api;
        sources.forEach(s -> delivered.put(s, null));
    }

    static boolean enabled(Map<String, String> env) {
        return Features.enabled(env, "wa");
    }

    static List<Source> sources(Map<String, String> env) {
        var extra = env.getOrDefault("RSE_LIB_WA_CASE_EVENT_SOURCES", "");
        return parseSources(DEFAULT_SOURCE + "," + extra);
    }

    /**
     * WA's own roles, plus the service's in RSE_LIB_WA_SYSTEM_USER_ROLES. In an environment the system user reads
     * each onboarded service's cases through that service's IDAM roles, such as civil's caseworker-civil-systemupdate,
     * as caseworker-wa-task-configuration alone is rarely granted enough of a case to configure its tasks.
     */
    static String[] systemUserRoles(Map<String, String> env) {
        var roles = new LinkedHashSet<>(List.of("caseworker", "caseworker-wa", "caseworker-wa-task-configuration"));
        Arrays.stream(env.getOrDefault("RSE_LIB_WA_SYSTEM_USER_ROLES", "").split(","))
            .map(String::trim)
            .filter(role -> !role.isEmpty())
            .forEach(roles::add);
        return roles.toArray(String[]::new);
    }

    /**
     * Once cftlib has booted, deploy WA's task process, create its system user and start delivering case events.
     */
    static void startWhenBooted(CFTLibApiImpl api) {
        if (!enabled(System.getenv())) {
            return;
        }
        var thread = new Thread(() -> {
            var wa = new WorkAllocation(api, sources(System.getenv()));
            // Before the services boot, so that cases a CFTLibConfigurer or a test creates are this run's.
            ControlPlane.waitForDB();
            wa.started = wa.databaseTime();
            ControlPlane.waitForBoot();
            wa.prepare();
            instance = wa;
            wa.deliverContinuously();
        });
        thread.setName("cftlib work allocation");
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler(ControlPlane.failFast);
        thread.start();
    }

    static WorkAllocation get() {
        if (!enabled(System.getenv())) {
            throw new IllegalStateException("Work allocation needs the wa feature.");
        }
        Awaitility.await().atMost(5, TimeUnit.MINUTES).until(() -> instance != null);
        return instance;
    }

    @SneakyThrows
    private void prepare() {
        api.createIdamUser(SYSTEM_USERNAME, systemUserRoles(System.getenv()));
        try (var bpmn = getClass().getResourceAsStream("/wa/wa-task-initiation-ia-asylum.bpmn")) {
            // WA deploys its task process without a tenant, so every service's DMNs can start it. Under its own
            // name, so that Camunda recognises it on the next boot and keeps the version it has.
            deploy(null, Map.of("wa-task-initiation-ia-asylum.bpmn", bpmn.readAllBytes()));
        }
        for (var source : delivered.keySet()) {
            delivered.put(source, newestEventBefore(source, started));
        }
    }

    /**
     * Deploy BPMN and DMN files to Camunda, once it is up. Camunda keeps a deployed file until it changes.
     */
    @SneakyThrows
    void importCamundaDefinitions(String tenantId, File... files) {
        var resources = new LinkedHashMap<String, byte[]>();
        for (var file : files) {
            resources.put(file.getName(), Files.readAllBytes(file.toPath()));
        }
        deploy(tenantId, resources);
    }

    /**
     * Deploy BPMN and DMN resources by name. Camunda only makes a new version of a resource whose content changed.
     */
    @SneakyThrows
    private void deploy(String tenantId, Map<String, byte[]> resources) {
        var engine = camundaUrl();
        Awaitility.await().atMost(5, TimeUnit.MINUTES).pollInterval(Duration.ofSeconds(2)).ignoreExceptions()
            .until(() -> http.send(HttpRequest.newBuilder(URI.create(engine + "/engine")).build(),
                HttpResponse.BodyHandlers.discarding()).statusCode() == 200);

        var boundary = UUID.randomUUID().toString();
        var body = new ByteArrayOutputStream();
        var fields = new LinkedHashMap<String, String>();
        fields.put("deployment-name", "cftlib-" + (tenantId == null ? "shared" : tenantId));
        fields.put("deployment-source", "cftlib");
        fields.put("deploy-changed-only", "true");
        if (tenantId != null) {
            fields.put("tenant-id", tenantId);
        }
        for (var field : fields.entrySet()) {
            body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field.getKey()
                + "\"\r\n\r\n" + field.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        for (var resource : resources.entrySet()) {
            body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + resource.getKey()
                + "\"; filename=\"" + resource.getKey() + "\"\r\nContent-Type: application/octet-stream\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
            body.writeBytes(resource.getValue());
            body.writeBytes("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        body.writeBytes(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        var response = http.send(HttpRequest.newBuilder(URI.create(engine + "/deployment/create"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build(),
            HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Camunda refused the deployment: " + response.body());
        }
    }

    @SneakyThrows
    private void deliverContinuously() {
        while (true) {
            try {
                deliverCaseEvents();
            } catch (Exception e) {
                // Tried again on the next pass. A test calling deliverCaseEvents sees the error itself.
                System.out.println("**** cftlib could not deliver a case event to work allocation: " + e.getMessage());
                Thread.sleep(5000);
            }
            Thread.sleep(500);
        }
    }

    /**
     * Post every case event not yet delivered to wa-case-event-handler, oldest first. Returns once they have all been
     * handled, so a test can check the tasks they create straight away.
     */
    @SneakyThrows
    synchronized void deliverCaseEvents() {
        for (var entry : delivered.entrySet()) {
            var source = entry.getKey();
            try (var c = connect(source)) {
                var query = c.prepareStatement("select id, message_information::text from " + source.schema()
                    + ".message_queue_candidates where id > ? and message_type = 'CASE_EVENT' order by id");
                query.setLong(1, entry.getValue() == null ? 0 : entry.getValue());
                var events = query.executeQuery();
                while (events.next()) {
                    var id = events.getLong(1);
                    var sent = deliver(source, id, events.getString(2));
                    entry.setValue(id);
                    if (sent) {
                        var published = c.prepareStatement("update " + source.schema()
                            + ".message_queue_candidates set published = now() where id = ?");
                        published.setLong(1, id);
                        published.execute();
                    }
                }
            } catch (SQLException e) {
                // A decentralised service creates its table when it first starts; until then there is nothing to send.
                if (!"42P01".equals(e.getSQLState())) {
                    throw e;
                }
            }
        }
    }

    /**
     * Post a case event, retrying it on later passes so that each case's events stay in order. As WA's Service Bus
     * subscription dead-letters a message it keeps failing, an event still rejected after a few attempts is set aside,
     * so that it cannot hold up every later event. Returns whether the event was delivered.
     */
    private boolean deliver(Source source, long id, String event) {
        var key = source + ":" + id;
        try {
            post(event);
            attempts.remove(key);
            return true;
        } catch (RuntimeException e) {
            int attempt = attempts.merge(key, 1, Integer::sum);
            if (attempt < MAX_DELIVERY_ATTEMPTS) {
                throw e;
            }
            attempts.remove(key);
            System.out.println("**** cftlib gave up delivering case event " + id + " from " + source + " to work "
                + "allocation after " + attempt + " attempts, and moved on to the next: " + e.getMessage());
            return false;
        }
    }

    @SneakyThrows
    private void post(String event) {
        var response = http.send(HttpRequest.newBuilder(URI.create(CASE_EVENT_HANDLER_URL + "/messages"))
                .header("Content-Type", "application/json")
                .header("ServiceAuthorization", api.generateDummyS2SToken("wa_case_event_handler"))
                .POST(HttpRequest.BodyPublishers.ofString(event))
                .build(),
            HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            // Retried on the next pass; later events wait so that each case's events stay in order.
            throw new IllegalStateException("wa-case-event-handler rejected case event with " + response.statusCode()
                + ": " + response.body() + "\n" + event);
        }
    }

    /**
     * Run one of wa-task-monitor's jobs, such as TERMINATION, which in an environment run on a schedule.
     */
    @SneakyThrows
    void runTaskMonitorJob(String job) {
        var response = http.send(HttpRequest.newBuilder(URI.create(TASK_MONITOR_URL + "/monitor/tasks/jobs"))
                .header("Content-Type", "application/json")
                .header("ServiceAuthorization", api.generateDummyS2SToken("wa_task_monitor"))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(Map.of("job_details", Map.of("name", job)))))
                .build(),
            HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("wa-task-monitor job " + job + " failed: " + response.body());
        }
    }

    // The time in the data store's database, whose clock stamps each event, in the session time zone the services
    // also use.
    @SneakyThrows
    private LocalDateTime databaseTime() {
        try (var c = connect(parseSources(DEFAULT_SOURCE).get(0))) {
            var result = c.prepareStatement("select localtimestamp").executeQuery();
            result.next();
            return result.getTimestamp(1).toLocalDateTime();
        }
    }

    @SneakyThrows
    private Long newestEventBefore(Source source, LocalDateTime time) {
        try (var c = connect(source)) {
            var query = c.prepareStatement("select coalesce(max(id), 0) from " + source.schema()
                + ".message_queue_candidates where time_stamp < ?");
            query.setTimestamp(1, Timestamp.valueOf(time));
            var result = query.executeQuery();
            result.next();
            return result.getLong(1);
        } catch (SQLException e) {
            if ("42P01".equals(e.getSQLState())) {
                return 0L;
            }
            throw e;
        }
    }

    private Connection connect(Source source) throws SQLException {
        return DriverManager.getConnection("jdbc:postgresql://" + CftlibConfig.dbHost() + ":" + CftlibConfig.dbPort()
            + "/" + source.database(), CftlibConfig.dbUsername(), CftlibConfig.dbPassword());
    }

    private static String camundaUrl() {
        return "http://localhost:" + ControlPlane.getEnvVar("CAMUNDA_PORT", 8999) + "/engine-rest";
    }
}
