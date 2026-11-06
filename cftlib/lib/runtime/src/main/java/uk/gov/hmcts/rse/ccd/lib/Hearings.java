package uk.gov.hmcts.rse.ccd.lib;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import lombok.SneakyThrows;
import org.awaitility.Awaitility;

/**
 * Hearings with the hmc feature.
 *
 * <p>hmc-cft-hearing-service and both HMI adapters run in-process on the Service Bus emulator. HMI itself, the
 * scheduling and listing system HMC sends hearing requests to, is a stand-in served by the runtime under /hmi: it
 * accepts every request, unless a test asks it to reject the next one, and lists a hearing only when a test sends
 * HMI's listing to the inbound adapter.
 */
public class Hearings {
    static final String SYSTEM_USERNAME = "hmc-system-user@cftlib.local";
    static final String SYSTEM_PASSWORD = "password";
    // The port Service.hmcHmiInboundAdapter pins.
    static final String INBOUND_ADAPTER_URL = "http://localhost:4559";
    static final String IDAM_URL = "http://localhost:5062";
    // In java.io.tmpdir, as the hmcCftHearingService service's arguments expect.
    static final String ROOM_ATTRIBUTES_FILE = "cftlib-hmc-room-attributes.json";

    // The hearing id that HMC sends HMI in a new hearing's request, as the hearing's case listing.
    private static final Pattern CASE_LISTING_ID = Pattern.compile("\"caseListingRequestId\"\\s*:\\s*\"([^\"]+)\"");
    // The case a request to HMI is for.
    private static final Pattern CASE_REFERENCE = Pattern.compile("\"caseIdHMCTS\"\\s*:\\s*\"([^\"]+)\"");

    record HmiRequest(String method, String hearingId, String body) {
    }

    public record Rejection(int errorCode, String description) {
    }

    private static final List<HmiRequest> requests = new CopyOnWriteArrayList<>();
    // Rejections for any case's next request, and for a particular case's.
    private static final Queue<Rejection> rejections = new ConcurrentLinkedQueue<>();
    private static final Map<String, Queue<Rejection>> caseRejections = new ConcurrentHashMap<>();
    private static volatile boolean ready;

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
            ready = true;
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
        Awaitility.await().atMost(5, TimeUnit.MINUTES).until(() -> ready);
        return new Hearings(api);
    }

    /**
     * The id the IDAM simulator gives the user with this email.
     */
    static String idamUserId(String email) {
        return UUID.nameUUIDFromBytes(email.getBytes(StandardCharsets.UTF_8)).toString();
    }

    /**
     * Record a request the outbound adapter sent HMI, and return the rejection a test asked for, if any.
     */
    public static Rejection receive(String method, String hearingId, String body) {
        if (hearingId == null) {
            var matcher = CASE_LISTING_ID.matcher(body == null ? "" : body);
            hearingId = matcher.find() ? matcher.group(1) : null;
        }
        requests.add(new HmiRequest(method, hearingId, body));
        var caseReference = CASE_REFERENCE.matcher(body == null ? "" : body);
        var forCase = caseReference.find() ? caseRejections.get(caseReference.group(1)) : null;
        var rejection = forCase == null ? null : forCase.poll();
        return rejection != null ? rejection : rejections.poll();
    }

    List<String> hmiRequests(String hearingId) {
        return requests.stream()
            .filter(r -> hearingId.equals(r.hearingId()))
            .map(r -> r.method() + " " + r.body())
            .toList();
    }

    void rejectNextHmiRequest(int errorCode, String description) {
        rejections.add(new Rejection(errorCode, description));
    }

    void rejectNextHmiRequest(String caseReference, int errorCode, String description) {
        caseRejections.computeIfAbsent(caseReference, c -> new ConcurrentLinkedQueue<>())
            .add(new Rejection(errorCode, description));
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
