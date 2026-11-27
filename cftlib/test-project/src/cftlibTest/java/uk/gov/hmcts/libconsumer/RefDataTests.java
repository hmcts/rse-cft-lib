package uk.gov.hmcts.libconsumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.stream.StreamSupport;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import uk.gov.hmcts.rse.ccd.lib.test.CftlibTest;

/**
 * The refData feature's stand-in for the reference data ExUI reads, serving src/cftlib/refdata and the judge
 * CFTLibConfig names.
 */
// The same context as LibConsumerApplicationTests, which Spring reuses rather than starting another on the same port.
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RefDataTests extends CftlibTest {

    // cftlib's runtime, on the S2S port this project sets.
    private static final String RUNTIME = "http://localhost:7431";

    private final HttpClient http = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    @Test
    void listsAServicesValuesAndTheValuesForEveryService() {
        var hearingTypes = get("/refdata/commondata/lov/categories/HearingType?serviceId=ABA1")
            .getAsJsonObject().getAsJsonArray("list_of_values");
        assertThat(keys(hearingTypes, "key"), contains("ABA1-FHR", "ABA1-DIR"));

        var channels = get("/refdata/commondata/lov/categories/HearingChannel?serviceId=ABA1")
            .getAsJsonObject().getAsJsonArray("list_of_values");
        assertThat(keys(channels, "key"), contains("INTER", "VID"));

        var unknown = get("/refdata/commondata/lov/categories/JudgeType?serviceId=ABA1")
            .getAsJsonObject().getAsJsonArray("list_of_values");
        assertThat(unknown.size(), equalTo(0));
    }

    @Test
    void servesAServicesCaseFlags() {
        var flags = get("/refdata/commondata/caseflags/service-id=ABA1?flag-type=PARTY")
            .getAsJsonObject().getAsJsonArray("flags").get(0).getAsJsonObject().getAsJsonArray("FlagDetails");
        assertThat(keys(flags, "name"), contains("Party"));
        assertThat(flags.get(0).getAsJsonObject().has("name_cy"), equalTo(false));

        assertThat(status("/refdata/commondata/caseflags/service-id=XYZ9"), equalTo(404));
    }

    @Test
    void findsCourtVenues() {
        assertThat(keys(get("/refdata/location/court-venues?epimms_id=827534").getAsJsonArray(), "site_name"),
            contains("Aberystwyth Justice Centre"));
        assertThat(keys(get("/refdata/location/court-venues/venue-search?search-string=london").getAsJsonArray(),
            "epimms_id"), contains("20262"));
    }

    @Test
    void offersTheJudgesTheServiceNames() {
        var found = post("/refdata/judicial/users/search", "{\"searchString\": \"quick\", \"serviceCode\": \"ABA1\"}");
        assertThat(found.size(), equalTo(1));
        var judge = found.get(0).getAsJsonObject();
        assertThat(judge.get("fullName").getAsString(), equalTo("Quick Judge"));
        assertThat(judge.get("personalCode").getAsString(), equalTo("9000001"));

        var byCode = post("/refdata/judicial/users", "{\"personal_code\": [\"9000001\"]}");
        assertThat(keys(byCode, "sidam_id"), contains(judge.get("idamId").getAsString()));
    }

    private static List<String> keys(JsonArray values, String field) {
        return StreamSupport.stream(values.spliterator(), false)
            .map(value -> value.getAsJsonObject().get(field).getAsString())
            .toList();
    }

    @SneakyThrows
    private JsonElement get(String path) {
        var response = http.send(HttpRequest.newBuilder(URI.create(RUNTIME + path)).build(),
            HttpResponse.BodyHandlers.ofString());
        assertThat(path, response.statusCode(), equalTo(200));
        return gson.fromJson(response.body(), JsonElement.class);
    }

    @SneakyThrows
    private int status(String path) {
        return http.send(HttpRequest.newBuilder(URI.create(RUNTIME + path)).build(),
            HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @SneakyThrows
    private JsonArray post(String path, String body) {
        var request = HttpRequest.newBuilder(URI.create(RUNTIME + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(path, response.statusCode(), equalTo(200));
        return gson.fromJson(response.body(), JsonArray.class);
    }
}
