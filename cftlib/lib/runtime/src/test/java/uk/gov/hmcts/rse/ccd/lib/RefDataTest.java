package uk.gov.hmcts.rse.ccd.lib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RefDataTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path root;
    private RefData refData;

    @BeforeEach
    void setUp() {
        refData = new RefData(root);
        RefData.clearJudges();
    }

    @AfterEach
    void tearDown() {
        RefData.clearJudges();
    }

    @Test
    void listsAServicesValuesInOrder() throws IOException {
        write("lov/HearingType.json", """
            [
              {"service_id": "AAA7", "key": "AAA7-DIS", "value_en": "Disposal", "lov_order": 2},
              {"service_id": "AAA7", "key": "AAA7-TRI", "value_en": "Trial", "lov_order": 1},
              {"service_id": "ABA5", "key": "ABA5-FHR", "value_en": "First Hearing", "lov_order": 1}
            ]
            """);

        var values = refData.listOfValues("HearingType", "AAA7", null, null, null, null);

        assertThat(values).extracting(value -> value.get("key").asText()).containsExactly("AAA7-TRI", "AAA7-DIS");
        assertThat(values.get(0).get("category_key").asText()).isEqualTo("HearingType");
        assertThat(values.get(0).get("active_flag").asText()).isEqualTo("Y");
        assertThat(values.get(0).has("service_id")).isFalse();
        assertThat(values.get(0).get("child_nodes").isNull()).isTrue();
    }

    @Test
    void fallsBackToTheValuesForEveryServiceAsRdCommondataDoes() throws IOException {
        write("lov/HearingChannel.json", """
            [
              {"key": "INTER", "value_en": "In Person", "lov_order": 1},
              {"service_id": "ABA5", "key": "VID", "value_en": "Video", "lov_order": 1}
            ]
            """);

        assertThat(refData.listOfValues("HearingChannel", "AAA7", null, null, null, null))
            .extracting(value -> value.get("key").asText()).containsExactly("INTER");
        assertThat(refData.listOfValues("HearingChannel", "ABA5", null, null, null, null))
            .extracting(value -> value.get("key").asText()).containsExactly("VID");
    }

    @Test
    void readsAResponseSavedFromRdCommondataForAService() throws IOException {
        // As GET /refdata/commondata/lov/categories/caseType?serviceId=AAA7&isChildRequired=Y returns it.
        write("lov/AAA7/caseType.json", """
            {"list_of_values": [
              {"category_key": "caseType", "key": "AAA7-SMALL", "value_en": "Small Claims", "lov_order": 1,
               "parent_category": "", "parent_key": "", "active_flag": "Y",
               "child_nodes": [
                 {"category_key": "caseSubType", "key": "AAA7-SMALL-SUB", "value_en": "Small Claims", "lov_order": 1,
                  "parent_category": "caseType", "parent_key": "AAA7-SMALL", "active_flag": "Y", "child_nodes": null}
               ]}
            ]}
            """);

        var withChildren = refData.listOfValues("caseType", "AAA7", null, null, null, "Y");
        assertThat(withChildren).hasSize(1);
        assertThat(withChildren.get(0).get("child_nodes")).hasSize(1);
        assertThat(withChildren.get(0).get("child_nodes").get(0).get("key").asText()).isEqualTo("AAA7-SMALL-SUB");

        assertThat(refData.listOfValues("caseType", "AAA7", null, null, null, "N").get(0).get("child_nodes").isNull())
            .isTrue();
        assertThat(refData.listOfValues("caseSubType", "AAA7", "caseType", "AAA7-SMALL", null, null))
            .extracting(value -> value.get("key").asText()).containsExactly("AAA7-SMALL-SUB");
    }

    @Test
    void listsNothingForACategoryWithNoValues() {
        assertThat(refData.listOfValues("JudgeType", "AAA7", null, null, null, null)).isEmpty();
    }

    @Test
    void filtersAServicesCaseFlagsAsRdCommondataDoes() throws IOException {
        write("caseflags/AAA7.json", """
            {"flags": [{"FlagDetails": [
              {"name": "Case", "name_cy": "Achos", "isParent": true, "flagCode": "CATGRY", "Path": [],
               "childFlags": [
                 {"name": "Urgent case", "name_cy": "Achos brys", "isParent": false, "flagCode": "CF0007",
                  "externallyAvailable": false, "Path": ["Case"], "childFlags": []}
               ]},
              {"name": "Party", "isParent": true, "flagCode": "CATGRY", "Path": [],
               "childFlags": [
                 {"name": "Vulnerable user", "isParent": false, "flagCode": "PF0002", "externallyAvailable": true,
                  "Path": ["Party"], "childFlags": []}
               ]}
            ]}]}
            """);

        var all = refData.caseFlags("aaa7", null, "Y", null).orElseThrow();
        assertThat(details(all)).extracting(flag -> flag.get("name").asText()).containsExactly("Case", "Party");
        assertThat(details(all).get(0).get("name_cy").asText()).isEqualTo("Achos");

        var party = refData.caseFlags("AAA7", "PARTY", null, null).orElseThrow();
        assertThat(details(party)).extracting(flag -> flag.get("name").asText()).containsExactly("Party");

        assertThat(details(refData.caseFlags("AAA7", null, "N", null).orElseThrow()).get(0).has("name_cy"))
            .isFalse();

        var external = refData.caseFlags("AAA7", null, null, "Y").orElseThrow();
        assertThat(details(external)).extracting(flag -> flag.get("name").asText()).containsExactly("Party");

        assertThat(refData.caseFlags("ABA5", null, null, null)).isEmpty();
    }

    @Test
    void findsCourtVenuesByIdAndBySearch() throws IOException {
        write("court-venues.json", """
            [
              {"epimms_id": "20262", "court_name": "Central London County Court", "site_name": "Central London",
               "postcode": "WC2A 2LL", "court_type_id": "10", "is_hearing_location": "Y"},
              {"epimms_id": "827534", "court_name": "Aberystwyth Justice Centre", "site_name": "Aberystwyth",
               "postcode": "SY23 1AS", "court_type_id": "10", "is_hearing_location": "N"},
              {"epimms_id": "111", "court_name": "London Tribunal", "site_name": "London Tribunal",
               "postcode": "E1 1AA", "court_type_id": "17", "is_hearing_location": "Y"}
            ]
            """);

        assertThat(refData.courtVenues(Map.of("epimms_id", "20262, 827534")))
            .extracting(venue -> venue.get("epimms_id").asText()).containsExactly("20262", "827534");
        assertThat(refData.courtVenues(Map.of("is_hearing_location", "Y", "court_type_id", "10")))
            .extracting(venue -> venue.get("epimms_id").asText()).containsExactly("20262");
        assertThat(refData.searchCourtVenues(Map.of("search-string", "london")))
            .extracting(venue -> venue.get("epimms_id").asText()).containsExactly("20262", "111");
        assertThat(refData.searchCourtVenues(Map.of("search-string", "london", "court-type-id", "10")))
            .extracting(venue -> venue.get("epimms_id").asText()).containsExactly("20262");
        assertThat(refData.searchCourtVenues(Map.of("search-string", "sy23")))
            .extracting(venue -> venue.get("epimms_id").asText()).containsExactly("827534");
    }

    @Test
    void judgesAreTheIdamUsersAServiceNames() throws IOException {
        RefData.recordIdamUser("Amy.Powell@example.com", "idam-1", "Amy", "Powell");
        RefData.recordIdamUser("caseworker@example.com", "idam-2", "Case", "Worker");
        RefData.addJudge("amy.powell@example.com", "9000001");

        var byCode = refData.judicialUsers(MAPPER.readTree("{\"personal_code\": [\"9000001\"]}"));
        assertThat(byCode).hasSize(1);
        assertThat(byCode.get(0).get("sidam_id").asText()).isEqualTo("idam-1");
        assertThat(byCode.get(0).get("full_name").asText()).isEqualTo("Amy Powell");
        assertThat(byCode.get(0).get("initials").asText()).isEqualTo("AP");
        assertThat(refData.judicialUsers(MAPPER.readTree("{\"sidam_ids\": [\"idam-1\", \"idam-2\"]}"))).hasSize(1);

        var found = refData.searchJudicialUsers(
            MAPPER.readTree("{\"searchString\": \"powe\", \"serviceCode\": \"AAA7\"}"));
        assertThat(found).extracting(judge -> judge.get("personalCode").asText()).containsExactly("9000001");
        assertThat(found.get(0).get("idamId").asText()).isEqualTo("idam-1");
        assertThat(refData.searchJudicialUsers(MAPPER.readTree("{\"searchString\": \"worker\"}"))).isEmpty();
    }

    @Test
    void judgesMustBeIdamUsersFirst() {
        assertThatThrownBy(() -> RefData.addJudge("nobody@example.com", "1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("createIdamUser");
    }

    private static List<JsonNode> details(JsonNode response) {
        var details = new java.util.ArrayList<JsonNode>();
        response.get("flags").get(0).get("FlagDetails").forEach(details::add);
        return details;
    }

    private void write(String path, String json) throws IOException {
        var file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, json);
    }
}
