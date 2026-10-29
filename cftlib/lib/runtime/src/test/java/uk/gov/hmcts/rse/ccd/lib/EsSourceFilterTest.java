package uk.gov.hmcts.rse.ccd.lib;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class EsSourceFilterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static final String DOCUMENT = """
        {
          "id": "1",
          "reference": 1234567890123456,
          "state": "Draft",
          "data": {
            "caseNameHmctsInternal": "Smith v Jones",
            "applicant1FirstName": "Ann",
            "caseManagementCategory": {
              "value": {"code": "C1", "label": "Category 1"},
              "list_items": [{"code": "C1"}]
            },
            "SearchCriteria": {
              "OtherCaseReferences": [{"id": "1", "value": "REF-001"}],
              "SearchParties": [{"id": "1", "value": {"Name": "Ann"}}]
            }
          },
          "supplementary_data": {"HMCTSServiceId": "AAA1", "orgs_assigned_users": {"ABC": 1}}
        }
        """;

    private JsonNode filter(String... includes) throws Exception {
        return EsSourceFilter.apply(mapper.readTree(DOCUMENT), List.of(includes));
    }

    @Test
    void noIncludesKeepsEverything() throws Exception {
        assertThat(EsSourceFilter.apply(mapper.readTree(DOCUMENT), List.of()))
            .isEqualTo(mapper.readTree(DOCUMENT));
    }

    @Test
    void namedTopLevelFieldsAreKeptWholeAndTheRestDropped() throws Exception {
        var filtered = filter("reference", "data");

        assertThat(filtered.fieldNames()).toIterable().containsExactlyInAnyOrder("reference", "data");
        assertThat(filtered.at("/data/applicant1FirstName").asText()).isEqualTo("Ann");
    }

    @Test
    void nestedIncludePrunesItsSiblings() throws Exception {
        var filtered = filter("data.caseManagementCategory.value.code");

        assertThat(filtered.at("/data/caseManagementCategory/value/code").asText()).isEqualTo("C1");
        assertThat(filtered.at("/data/caseManagementCategory/value").has("label")).isFalse();
        assertThat(filtered.at("/data/caseManagementCategory").has("list_items")).isFalse();
        assertThat(filtered.at("/data").has("applicant1FirstName")).isFalse();
    }

    @Test
    void anIncludeKeepsACollectionWhole() throws Exception {
        var filtered = filter("data.SearchCriteria.OtherCaseReferences");

        assertThat(filtered.at("/data/SearchCriteria/OtherCaseReferences/0/value").asText()).isEqualTo("REF-001");
        assertThat(filtered.at("/data/SearchCriteria").has("SearchParties")).isFalse();
    }

    @Test
    void trailingWildcardKeepsEveryDescendant() throws Exception {
        var filtered = filter("supplementary_data.*");

        assertThat(filtered.at("/supplementary_data/HMCTSServiceId").asText()).isEqualTo("AAA1");
        assertThat(filtered.at("/supplementary_data/orgs_assigned_users/ABC").asInt()).isEqualTo(1);
        assertThat(filtered.has("data")).isFalse();
    }

    @Test
    void includeThatMatchesNothingLeavesAnEmptyDocument() throws Exception {
        assertThat(filter("data.nothingHere").isEmpty()).isTrue();
    }
}
