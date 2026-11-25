package uk.gov.hmcts.rse.ccd.lib;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import lombok.SneakyThrows;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;

/**
 * Hearings with the hmc feature.
 *
 * <p>hmc-cft-hearing-service and both HMI adapters run in-process on the Service Bus emulator. HMI itself, the
 * scheduling and listing system HMC sends hearing requests to, is a stand-in served by the runtime under /hmi. It
 * accepts every request, unless asked to reject the next one, and answers a hearing as List Assist would, by listing
 * it, cancelling it or reporting it cannot list it, when a test or someone on the runtime's /hmi page asks it to, or
 * by itself with RSE_LIB_HMI_AUTO_RESPOND.
 */
public class Hearings {
    static final String SYSTEM_USERNAME = "hmc-system-user@cftlib.local";
    static final String SYSTEM_PASSWORD = "password";
    // The port Service.hmcHmiInboundAdapter pins.
    static final String INBOUND_ADAPTER_URL = "http://localhost:4559";
    // The port Service.hmcCftHearingService pins.
    static final int HEARING_SERVICE_PORT = 4561;
    // The hearing service's database, as Service.Hmc names it.
    static final String HMC_DATABASE = "hmc_cft_hearing_service";
    static final String IDAM_URL = "http://localhost:5062";
    // In java.io.tmpdir, as the hmcCftHearingService service's arguments expect.
    static final String ROOM_ATTRIBUTES_FILE = "cftlib-hmc-room-attributes.json";
    static final String AUTO_RESPOND = "RSE_LIB_HMI_AUTO_RESPOND";

    // HMC's statuses for a hearing whose request HMI has acknowledged, by the request's method.
    private static final Map<String, String> ACKNOWLEDGED = Map.of(
        "POST", "AWAITING_LISTING",
        "PUT", "UPDATE_SUBMITTED",
        "DELETE", "CANCELLATION_SUBMITTED"
    );
    private static final Duration ACKNOWLEDGEMENT_WAIT = Duration.ofMinutes(1);

    // The hearing id that HMC sends HMI in a new hearing's request, as the hearing's case listing.
    private static final Pattern CASE_LISTING_ID = Pattern.compile("\"caseListingRequestId\"\\s*:\\s*\"([^\"]+)\"");
    // The case a request to HMI is for.
    private static final Pattern CASE_REFERENCE = Pattern.compile("\"caseIdHMCTS\"\\s*:\\s*\"([^\"]+)\"");

    public record Rejection(int errorCode, String description) {
    }

    /**
     * A hearing HMC has sent HMI requests for, as the /hmi page shows it.
     *
     * @param lastRequest    the method of the last request HMC sent for it: POST, PUT or DELETE
     * @param hmcStatus      the hearing's status in HMC
     * @param suggestedStart when the hearing would be listed if nobody chose a time, in court time
     * @param unreadable     why HMC's request for the hearing couldn't be read, if it couldn't
     */
    public record HmiHearing(String hearingId, String caseReference, String caseTitle, String lastRequest,
                             Instant lastReceived, String hmcStatus, LocalDateTime suggestedStart, String venue,
                             int duration, String judge, String unreadable) {
    }

    // Kept in a file, so the /hmi page still lists hearings awaiting an answer after a restart. Read once HMC's
    // database is up, the first time the requests are needed, which is only with the hmc feature on.
    private static final HmiRequestLog requests = HmiRequestLog.fromEnvironment(System.getenv(),
        Hearings::hmcDatabase);
    // Rejections for any case's next request, and for a particular case's.
    private static final Queue<Rejection> rejections = new ConcurrentLinkedQueue<>();
    private static final Map<String, Queue<Rejection>> caseRejections = new ConcurrentHashMap<>();
    private static final ExecutorService responder = Executors.newCachedThreadPool(runnable -> {
        var thread = new Thread(runnable, "cftlib hmi responder");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile boolean autoRespond = Boolean.parseBoolean(System.getenv(AUTO_RESPOND));
    private static volatile Hearings instance;

    private final CFTLibApiImpl api;
    private final HttpClient http = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    private Hearings(CFTLibApiImpl api) {
        this.api = api;
    }

    static boolean enabled(Map<String, String> env) {
        return Features.enabled(env, "hmc");
    }

    /**
     * Write the hearing service's room attributes where its ROOM_ATTRIBUTES_FILE points, before it starts. It reads
     * them with Resource.getFile, which cannot read a file inside its jar.
     */
    @SneakyThrows
    static void writeRoomAttributes(Map<String, String> env) {
        if (!enabled(env)) {
            return;
        }
        try (var in = Hearings.class.getResourceAsStream("/hmc/room-attributes.json")) {
            Files.write(Path.of(System.getProperty("java.io.tmpdir"), ROOM_ATTRIBUTES_FILE), in.readAllBytes());
        }
    }

    /**
     * Once cftlib has booted, create the system user the inbound adapter calls the hearing service as.
     */
    static void startWhenBooted(CFTLibApiImpl api) {
        if (!enabled(System.getenv())) {
            return;
        }
        var thread = new Thread(() -> {
            ControlPlane.waitForBoot();
            api.createIdamUser(SYSTEM_USERNAME, "caseworker", "hearing-manager");
            // HMC checks its callers' organisational role assignments. With no attributes this one covers every
            // jurisdiction and case type.
            api.configureRoleAssignments(new Gson().toJson(List.of(Map.of(
                "id", idamUserId(SYSTEM_USERNAME),
                "overrideAll", true,
                "roleAssignments", List.of(Map.of(
                    "roleType", "ORGANISATION",
                    "roleName", "hearing-manager",
                    "grantType", "STANDARD",
                    "roleCategory", "ADMIN",
                    "classification", "PUBLIC",
                    "readOnly", false,
                    "attributes", Map.of(),
                    "authorisations", List.of()
                ))
            ))));
            instance = new Hearings(api);
        });
        thread.setName("cftlib hearings");
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler(ControlPlane.failFast);
        thread.start();
    }

    static Hearings get(CFTLibApiImpl api) {
        if (!enabled(System.getenv())) {
            throw new IllegalStateException("Hearings need the hmc feature.");
        }
        Awaitility.await().atMost(5, TimeUnit.MINUTES).until(() -> instance != null);
        return instance;
    }

    /**
     * The hearings, for the /hmi page, or null until the hmc feature has started.
     */
    public static Hearings ready() {
        return enabled(System.getenv()) ? instance : null;
    }

    /**
     * The id the IDAM simulator gives the user with this email.
     */
    static String idamUserId(String email) {
        return UUID.nameUUIDFromBytes(email.getBytes(StandardCharsets.UTF_8)).toString();
    }

    /**
     * Record a request the outbound adapter sent HMI, and return the rejection asked for, if any. Without one, and
     * with automatic responses on, answer the request once HMC has seen HMI acknowledge it.
     */
    public static Rejection receive(String method, String hearingId, String body) {
        if (hearingId == null) {
            var matcher = CASE_LISTING_ID.matcher(body == null ? "" : body);
            hearingId = matcher.find() ? matcher.group(1) : null;
        }
        requests.add(new HmiRequestLog.HmiRequest(method, hearingId, body, Instant.now()));
        var caseReference = CASE_REFERENCE.matcher(body == null ? "" : body);
        var forCase = caseReference.find() ? caseRejections.get(caseReference.group(1)) : null;
        var rejection = forCase == null ? null : forCase.poll();
        if (rejection == null) {
            rejection = rejections.poll();
        }
        var hearings = instance;
        if (rejection == null && autoRespond && hearings != null && hearingId != null) {
            var id = hearingId;
            responder.execute(() -> hearings.respondOnceAcknowledged(method, id));
        }
        return rejection;
    }

    public static boolean autoResponding() {
        return autoRespond;
    }

    public static void autoRespond(boolean enabled) {
        autoRespond = enabled;
    }

    List<String> hmiRequests(String hearingId) {
        return requests.all().stream()
            .filter(r -> hearingId.equals(r.hearingId()))
            .map(r -> r.method() + " " + r.body())
            .toList();
    }

    public void rejectNextHmiRequest(int errorCode, String description) {
        rejections.add(new Rejection(errorCode, description));
    }

    public void rejectNextHmiRequest(String caseReference, int errorCode, String description) {
        caseRejections.computeIfAbsent(caseReference, c -> new ConcurrentLinkedQueue<>())
            .add(new Rejection(errorCode, description));
    }

    /**
     * The rejections waiting for the next request, as the /hmi page shows them: for any case, then by case.
     */
    public List<String> pendingRejections() {
        var pending = new ArrayList<String>();
        rejections.forEach(r -> pending.add("any case: " + r.errorCode() + " " + r.description()));
        caseRejections.forEach((caseReference, queue) -> queue.forEach(
            r -> pending.add("case " + caseReference + ": " + r.errorCode() + " " + r.description())));
        return pending;
    }

    /**
     * The hearings HMC has sent HMI requests for, most recently requested first, with their status in HMC.
     */
    @SneakyThrows
    public List<HmiHearing> hearings() {
        var byHearing = new LinkedHashMap<String, List<HmiRequestLog.HmiRequest>>();
        for (var request : requests.all()) {
            if (request.hearingId() != null) {
                byHearing.computeIfAbsent(request.hearingId(), id -> new ArrayList<>()).add(request);
            }
        }
        if (byHearing.isEmpty()) {
            return List.of();
        }
        Map<String, String> statuses;
        try (var connection = hmcConnection()) {
            statuses = hmcStatuses(connection, byHearing.keySet());
        }
        var today = LocalDate.now(HmiResponses.COURT_TIME);
        var hearings = new ArrayList<HmiHearing>();
        byHearing.forEach((hearingId, received) -> {
            var last = received.get(received.size() - 1);
            HmiResponses.Request request = null;
            String unreadable = null;
            // One request HMI can't read shows on its own row, rather than stopping the page.
            try {
                request = latestHearingRequest(received);
            } catch (RuntimeException e) {
                unreadable = String.valueOf(e.getMessage());
            }
            hearings.add(new HmiHearing(
                hearingId,
                request == null ? null : request.caseReference(),
                request == null ? null : request.caseTitle(),
                last.method(),
                last.received(),
                statuses.get(hearingId),
                request == null ? null : HmiResponses.defaultStart(request, today),
                request == null ? null : request.venue(),
                request == null ? HmiResponses.DEFAULT_DURATION_MINUTES : request.duration(),
                request == null ? null : request.judge(),
                unreadable
            ));
        });
        hearings.sort(Comparator.comparing(HmiHearing::lastReceived).reversed());
        return hearings;
    }

    /**
     * List the hearing as List Assist would, from the last request HMC sent for it: at the venue, for the duration
     * and with the judge it asked for.
     *
     * @param start when the hearing starts, in court time, or null for the first working day the request allows
     */
    public void list(String hearingId, LocalDateTime start) {
        var request = hearingRequest(hearingId);
        var at = start != null ? start : HmiResponses.defaultStart(request, LocalDate.now(HmiResponses.COURT_TIME));
        sendHmiListing(hearingId, HmiResponses.listing(hearingId, request, at, LocalDateTime.now(ZoneOffset.UTC)));
    }

    /**
     * Close the hearing as List Assist does once it has cancelled it, with the reason HMC gave if it asked.
     */
    public void cancel(String hearingId) {
        var request = hearingRequest(hearingId);
        var reason = requests.all().stream()
            .filter(r -> hearingId.equals(r.hearingId()) && "DELETE".equals(r.method()))
            .reduce((first, second) -> second)
            .map(r -> HmiResponses.cancellationReason(r.body()))
            .orElse(null);
        sendHmiListing(hearingId, HmiResponses.cancellation(hearingId, request, reason,
            LocalDateTime.now(ZoneOffset.UTC)));
    }

    /**
     * Report that List Assist cannot list the hearing, which HMC puts in EXCEPTION.
     */
    public void raiseException(String hearingId, int errorCode, String description) {
        sendHmiListing(hearingId, HmiResponses.exception(errorCode, description));
    }

    /**
     * Send HMI's response to a hearing request, such as its listing, to the inbound adapter as HMI would.
     */
    @SneakyThrows
    void sendHmiListing(String hearingId, String body) {
        var response = http.send(HttpRequest.newBuilder(URI.create(INBOUND_ADAPTER_URL + "/listings/" + hearingId))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + systemUserToken())
                // HMI reaches HMC through the API gateway.
                .header("ServiceAuthorization", api.generateDummyS2SToken("api_gw"))
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 202) {
            throw new IllegalStateException("hmc-hmi-inbound-adapter rejected the listing for hearing " + hearingId
                + " with " + response.statusCode() + ": " + response.body());
        }
    }

    /**
     * HMC's database. Hearings are read from it: reading a hearing through HMC's API checks the caller can access
     * the case in CCD, which cftlib's own user cannot.
     */
    private static Connection hmcConnection() throws SQLException {
        return DriverManager.getConnection(
            "jdbc:postgresql://" + CftlibConfig.dbHost() + ":" + CftlibConfig.dbPort() + "/" + HMC_DATABASE,
            CftlibConfig.dbUsername(), CftlibConfig.dbPassword());
    }

    /**
     * Identifies HMC's database, which a clean boot recreates, numbering its hearings from the start again: the
     * Postgres cluster's system identifier, which initdb sets, and the database's oid within it. A database user that
     * can't read the cluster's identifier gets the oid alone.
     */
    @SneakyThrows
    private static String hmcDatabase() {
        try (var connection = hmcConnection()) {
            String cluster;
            try (var query = connection.createStatement();
                 var result = query.executeQuery("select system_identifier from pg_control_system()")) {
                result.next();
                cluster = result.getString(1);
            } catch (SQLException e) {
                cluster = "cluster";
            }
            try (var query = connection.createStatement();
                 var result = query.executeQuery("select oid from pg_database where datname = current_database()")) {
                result.next();
                return cluster + "/" + result.getString(1);
            }
        }
    }

    /**
     * The hearings' statuses in HMC, UNKNOWN for those HMC has no such hearing for.
     */
    static Map<String, String> hmcStatuses(Connection connection, Collection<String> hearingIds) throws SQLException {
        var statuses = new HashMap<String, String>();
        var ids = new ArrayList<Long>();
        for (var hearingId : hearingIds) {
            statuses.put(hearingId, "UNKNOWN");
            try {
                ids.add(Long.parseLong(hearingId));
            } catch (NumberFormatException e) {
                // Not one of HMC's ids.
            }
        }
        if (ids.isEmpty()) {
            return statuses;
        }
        try (var query = connection.prepareStatement(
                "select hearing_id, status from hearing where hearing_id = any(?)")) {
            query.setArray(1, connection.createArrayOf("bigint", ids.toArray()));
            try (var result = query.executeQuery()) {
                while (result.next()) {
                    statuses.put(String.valueOf(result.getLong(1)), result.getString(2));
                }
            }
        }
        return statuses;
    }

    /**
     * Answer a request as List Assist would, once HMC has recorded HMI's acknowledgement: HMC only accepts a listing
     * or cancellation for a hearing awaiting one.
     */
    private void respondOnceAcknowledged(String method, String hearingId) {
        var acknowledged = ACKNOWLEDGED.get(method);
        try {
            try (var connection = hmcConnection()) {
                Awaitility.await().atMost(ACKNOWLEDGEMENT_WAIT).pollInterval(Duration.ofSeconds(1))
                    .until(() -> acknowledged.equals(hmcStatuses(connection, List.of(hearingId)).get(hearingId)));
            }
            if ("DELETE".equals(method)) {
                cancel(hearingId);
            } else {
                list(hearingId, null);
            }
        } catch (ConditionTimeoutException e) {
            System.out.println("**** cftlib's HMI did not respond to " + method + " for hearing " + hearingId
                + ": HMC did not move it to " + acknowledged + " within " + ACKNOWLEDGEMENT_WAIT.toSeconds() + "s");
        } catch (Exception e) {
            // Includes the checked exceptions the adapter's client and HMC's database throw.
            System.out.println("**** cftlib's HMI could not respond to " + method + " for hearing " + hearingId
                + ": " + e);
        }
    }

    private HmiResponses.Request hearingRequest(String hearingId) {
        var request = latestHearingRequest(
            requests.all().stream().filter(r -> hearingId.equals(r.hearingId())).toList());
        if (request == null) {
            throw new IllegalArgumentException("HMC has not sent HMI a request for hearing " + hearingId);
        }
        return request;
    }

    // The last request for a new hearing or an amendment, which says what to list.
    private static HmiResponses.Request latestHearingRequest(List<HmiRequestLog.HmiRequest> received) {
        for (int i = received.size() - 1; i >= 0; i--) {
            var request = received.get(i);
            if (!"DELETE".equals(request.method()) && request.body() != null) {
                return HmiResponses.read(request.body());
            }
        }
        return null;
    }

    /**
     * The case in ExUI, on the port it's published on.
     */
    public static String caseUrl(String caseReference) {
        var port = XuiApp.MANAGE_CASES.port(XuiEnvironment.interpolationSource(), XuiEnvironment.Network.HOST);
        return "http://localhost:" + port + "/cases/case-details/" + caseReference;
    }

    // The inbound adapter checks its caller's token against IDAM, so it needs a real one rather than a dummy.
    @SneakyThrows
    private String systemUserToken() {
        var form = Map.of(
            "grant_type", "password",
            "username", SYSTEM_USERNAME,
            "password", SYSTEM_PASSWORD,
            "client_id", "hmi",
            "client_secret", "hmi",
            "scope", "openid profile roles"
        );
        var encoded = String.join("&", form.entrySet().stream()
            .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
            .toList());
        var response = http.send(HttpRequest.newBuilder(URI.create(IDAM_URL + "/o/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encoded))
                .build(),
            HttpResponse.BodyHandlers.ofString());
        return gson.fromJson(response.body(), Map.class).get("access_token").toString();
    }
}
