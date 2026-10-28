package uk.gov.hmcts.rse.ccd.lib;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Checks one property over every clause the stub supports, in every position a bool can put it in:
 * the statement is given exactly as many values as it has parameters.
 *
 * <p>The translator binds a clause's values as it emits that clause's sql, so any path that emits
 * nothing, or emits a clause twice, leaves the two out of step and postgres rejects the statement
 * at runtime. Exercising the combinations mechanically covers far more of the query surface than
 * example based tests reach: an optional should alongside a must shipped broken precisely because
 * no example happened to combine those two.
 */
class EsQueryTranslatorInvariantTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Clauses that bind at least one value, so a leak or a double bind shows up. */
    private static List<String> leaves() {
        var wrapped = Base64.getEncoder().encodeToString(
            "{\"term\":{\"state\":\"Draft\"}}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return List.of(
            "{\"term\":{\"jurisdiction\":\"DIVORCE\"}}",
            "{\"term\":{\"data.applicant1FirstName.keyword\":\"Ann\"}}",
            "{\"terms\":{\"state\":[\"draft\",\"submitted\"]}}",
            "{\"terms\":{\"reference.keyword\":[\"1234567890123456\"]}}",
            "{\"term\":{\"supplementary_data.HMCTSServiceId.keyword\":\"AAA6\"}}",
            "{\"term\":{\"data_classification.applicant1FirstName\":\"PUBLIC\"}}",
            "{\"match\":{\"data.applicant1LastName\":{\"operator\":\"and\",\"query\":\"Smith Jones\"}}}",
            "{\"match\":{\"state\":\"Draft\"}}",
            "{\"match\":{\"reference\":\"1234-5678-9012-3456\"}}",
            "{\"match_phrase_prefix\":{\"reference\":\"1234-56\"}}",
            "{\"match_phrase\":{\"data.caseNameHmctsInternal\":\"Smith v Jones\"}}",
            "{\"match_phrase_prefix\":{\"data.CaseAccessCategory\":\"Civil\"}}",
            "{\"wildcard\":{\"data.applicant1LastName\":\"*smi*\"}}",
            "{\"range\":{\"created_date\":{\"gte\":\"2024-01-01\",\"lt\":\"2025-01-01\"}}}",
            "{\"range\":{\"data.dueDate\":{\"gt\":\"2024-06-01\"}}}",
            "{\"exists\":{\"field\":\"data.applicant1FirstName\"}}",
            "{\"wrapper\":{\"query\":\"" + wrapped + "\"}}",
            "{\"nested\":{\"path\":\"data.SearchCriteria.SearchParties.value\",\"query\":{\"bool\":{\"must\":["
                + "{\"match\":{\"data.SearchCriteria.SearchParties.value.Name\":\"Bob\"}},"
                + "{\"term\":{\"data.SearchCriteria.SearchParties.value.PostCode.keyword\":\"M1 1AA\"}}]}}}}",
            "{\"match_all\":{}}",
            "{\"match_none\":{}}");
    }

    static Stream<String> searches() {
        var bodies = new ArrayList<String>();
        for (var leaf : leaves()) {
            // Each position a bool can hold a clause in, and alongside a must, which is what
            // makes a should optional and so discardable.
            bodies.add(wrap("{\"bool\":{\"must\":[" + leaf + "]}}"));
            bodies.add(wrap("{\"bool\":{\"filter\":[" + leaf + "]}}"));
            bodies.add(wrap("{\"bool\":{\"must_not\":[" + leaf + "]}}"));
            bodies.add(wrap("{\"bool\":{\"should\":[" + leaf + "]}}"));
            bodies.add(wrap("{\"bool\":{\"must\":[{\"match_all\":{}}],\"should\":[" + leaf + "]}}"));
            bodies.add(wrap("{\"bool\":{\"filter\":[{\"match_all\":{}}],\"should\":[" + leaf + "]}}"));
            bodies.add(wrap("{\"bool\":{\"should\":[" + leaf + "],\"minimum_should_match\":0}}"));
            bodies.add(wrap("{\"bool\":{\"should\":[" + leaf + "],\"minimum_should_match\":1}}"));
            bodies.add(wrap("{\"bool\":{\"should\":[" + leaf + "," + leaf + "],\"minimum_should_match\":2}}"));
            bodies.add(wrap("{\"bool\":{\"should\":[" + leaf + "],\"minimum_should_match\":9}}"));
            // Nested inside the outer bool, which is the shape CCD's security filters produce.
            bodies.add(wrap("{\"bool\":{\"must\":[{\"bool\":{\"should\":[" + leaf + "]}}],"
                + "\"filter\":[{\"terms\":{\"state\":[\"draft\"]}}]}}"));
            bodies.add(wrap(leaf));
        }
        return bodies.stream();
    }

    private static String wrap(String query) {
        // A sort binds values of its own, and those are bound after the predicate's.
        return "{\"from\":0,\"size\":25,\"sort\":[{\"data.applicant1LastName.keyword\":\"asc\"},"
            + "{\"created_date\":\"desc\"}],\"query\":" + query + "}";
    }

    @ParameterizedTest
    @MethodSource("searches")
    void bindsOneValuePerParameter(String body) throws Exception {
        var search = new EsQueryTranslator(MAPPER).translate(MAPPER.readTree(body));

        assertThat(search.whereParams())
            .as("values bound for the where clause of %s, which is %s", body, search.where())
            .hasSize(placeholders(search.where()));

        for (var sort : search.sorts()) {
            assertThat(sort.params())
                .as("values bound for the sort %s of %s", sort.expression(), body)
                .hasSize(placeholders(sort.expression()));
        }
    }

    private int placeholders(String sql) {
        // '?' only ever appears as a parameter marker: every literal is bound, not interpolated.
        return (int) sql.chars().filter(c -> c == '?').count();
    }
}
