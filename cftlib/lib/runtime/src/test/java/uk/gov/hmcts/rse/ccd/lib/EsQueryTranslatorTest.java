package uk.gov.hmcts.rse.ccd.lib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EsQueryTranslatorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private EsQueryTranslator.Search translate(String json) {
        return new EsQueryTranslator(mapper).translate(parse(json));
    }

    private JsonNode parse(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Test
    void emptyBodyMatchesEverything() {
        var search = translate("{}");

        assertThat(search.where()).isEqualTo("true");
        assertThat(search.whereParams()).isEmpty();
        assertThat(search.from()).isZero();
        assertThat(search.size()).isEqualTo(10);
        assertThat(search.sorts()).isEmpty();
    }

    @Test
    void matchAll() {
        assertThat(translate("{\"query\":{\"match_all\":{}}}").where()).isEqualTo("true");
    }

    @Test
    void emptyBoolMustMatchesEverything() {
        // The query cftlib's own acceptance test sends for the case list.
        assertThat(translate("{\"query\":{\"bool\":{\"must\":[]}}}").where()).isEqualTo("true");
    }

    @Test
    void fromAndSize() {
        var search = translate("{\"from\":50,\"size\":25}");

        assertThat(search.from()).isEqualTo(50);
        assertThat(search.size()).isEqualTo(25);
    }

    @Test
    void termOnAnalysedMetadataMatchesTokens() {
        var search = translate("{\"query\":{\"term\":{\"state\":\"open\"}}}");

        assertThat(search.where())
            .isEqualTo("? = any(regexp_split_to_array(lower(u.state), '[^[:alnum:]_]+'))");
        assertThat(search.whereParams()).containsExactly("open");
    }

    @Test
    void termOnAnalysedMetadataLowercasesTheValue() {
        // Elasticsearch indexes 'Draft' as the token 'draft', so a term query on the text field
        // matches regardless of the stored case.
        var search = translate("{\"query\":{\"term\":{\"state\":{\"value\":\"Draft\",\"boost\":1.0}}}}");

        assertThat(search.whereParams()).containsExactly("draft");
    }

    @Test
    void termOnKeywordSubfieldIsExact() {
        var search = translate("{\"query\":{\"term\":{\"state.keyword\":\"Draft\"}}}");

        assertThat(search.where()).isEqualTo("u.state = ?");
        assertThat(search.whereParams()).containsExactly("Draft");
    }

    @Test
    void securityClassificationIsAlwaysExactBecauseItIsMappedAsAKeyword() {
        var search = translate("{\"query\":{\"terms\":{\"security_classification\":[\"PUBLIC\",\"PRIVATE\"]}}}");

        assertThat(search.where()).isEqualTo(
            "(u.security_classification = ? or u.security_classification = ?)");
        assertThat(search.whereParams()).containsExactly("PUBLIC", "PRIVATE");
    }

    @Test
    void termsOnReferenceCastsTheColumn() {
        var search = translate("{\"query\":{\"terms\":{\"reference.keyword\":[1234567890123456]}}}");

        assertThat(search.where()).isEqualTo("(u.reference::text = ?)");
        assertThat(search.whereParams()).containsExactly("1234567890123456");
    }

    @Test
    void emptyTermsMatchesNothing() {
        assertThat(translate("{\"query\":{\"terms\":{\"state\":[]}}}").where()).isEqualTo("false");
    }

    @Test
    void matchOnCaseDataAndsItsTokens() {
        // The shape XUI's bff builds for every case list filter.
        var search = translate(
            "{\"query\":{\"match\":{\"data.applicant1FirstName\":{\"operator\":\"and\",\"query\":\"Ann Marie\"}}}}");

        assertThat(search.where()).isEqualTo(
            "(exists (select 1 from jsonb_array_elements_text(jsonb_path_query_array(u.data, ?::jsonpath)) "
                + "as v(val) where ? = any(regexp_split_to_array(lower(v.val), '[^[:alnum:]_]+')))"
                + " and exists (select 1 from jsonb_array_elements_text(jsonb_path_query_array(u.data, ?::jsonpath)) "
                + "as v(val) where ? = any(regexp_split_to_array(lower(v.val), '[^[:alnum:]_]+'))))");
        assertThat(search.whereParams())
            .containsExactly("lax $.\"applicant1FirstName\"", "ann", "lax $.\"applicant1FirstName\"", "marie");
    }

    @Test
    void matchWithoutAnOperatorOrsItsTokens() {
        var search = translate("{\"query\":{\"match\":{\"data.name\":\"ann marie\"}}}");

        assertThat(search.where()).contains(" or ");
        assertThat(search.whereParams()).containsExactly("lax $.\"name\"", "ann", "lax $.\"name\"", "marie");
    }

    @Test
    void matchWithNoTokensMatchesNothing() {
        assertThat(translate("{\"query\":{\"match\":{\"data.name\":\"  -  \"}}}").where()).isEqualTo("false");
    }

    @Test
    void matchPhraseLooksForConsecutiveTokens() {
        var search = translate("{\"query\":{\"match_phrase\":{\"data.name\":\"Ann-Marie Smith\"}}}");

        assertThat(search.where()).contains("strpos((' ' || array_to_string(regexp_split_to_array(lower(v.val), "
            + "'[^[:alnum:]_]+'), ' ') || ' '), ?) > 0");
        assertThat(search.whereParams()).containsExactly("lax $.\"name\"", " ann marie smith ");
    }

    @Test
    void matchPhrasePrefixOnCaseAccessCategory() {
        // What the grant type builders emit for case access categories.
        var search = translate(
            "{\"query\":{\"match_phrase_prefix\":{\"data.CaseAccessCategory\":\"Civil/Standard\"}}}");

        assertThat(search.where()).contains(" ~ ?");
        assertThat(search.whereParams())
            .containsExactly("lax $.\"CaseAccessCategory\"", " civil standard[^ ]* ");
    }

    @Test
    void wildcardOnAKeywordFieldIsTranslatedToLike() {
        // Global search's case reference and party searches.
        var search = translate("{\"query\":{\"wildcard\":{\"reference.keyword\":{\"wildcard\":\"16*\"}}}}");

        assertThat(search.where()).isEqualTo("u.reference::text like ? escape '\\'");
        assertThat(search.whereParams()).containsExactly("16%");
    }

    @Test
    void wildcardOnAnAnalysedFieldMatchesTokens() {
        // XUI sends '*term*' lowercased for its configured wildcard search fields.
        var search = translate("{\"query\":{\"wildcard\":{\"data.caseName\":\"*smith*\"}}}");

        assertThat(search.where()).contains("unnest(regexp_split_to_array(lower(v.val), '[^[:alnum:]_]+'))");
        assertThat(search.whereParams()).containsExactly("lax $.\"caseName\"", "%smith%");
    }

    @Test
    void wildcardEscapesTheCharactersLikeTreatsSpecially() {
        assertThat(EsQueryTranslator.wildcardToLike("a*b?c_d%e")).isEqualTo("a%b_c\\_d\\%e");
    }

    @Test
    void rangeOnADateColumnComparesTimestamps() {
        var search = translate("{\"query\":{\"range\":{\"created_date\":{\"gte\":\"2024-01-01\"}}}}");

        assertThat(search.where()).isEqualTo("(u.created_date >= ?::timestamp)");
        assertThat(search.whereParams()).containsExactly("2024-01-01");
    }

    @Test
    void rangeAcceptsTheLegacyFromAndToForm() {
        // What org.elasticsearch's RangeQueryBuilder serialises before CCD normalises it.
        var search = translate("{\"query\":{\"range\":{\"data.dueDate\":"
            + "{\"from\":\"2024-01-01\",\"to\":\"2024-02-01\",\"include_lower\":true,\"include_upper\":false}}}}");

        assertThat(search.where()).contains("v.val >= ?").contains("v.val < ?");
        assertThat(search.whereParams()).containsExactly(
            "lax $.\"dueDate\"", "2024-01-01", "lax $.\"dueDate\"", "2024-02-01");
    }

    @Test
    void existsOnAColumn() {
        assertThat(translate("{\"query\":{\"exists\":{\"field\":\"last_modified\"}}}").where())
            .isEqualTo("u.last_modified is not null");
    }

    @Test
    void existsOnCaseData() {
        var search = translate("{\"query\":{\"exists\":{\"field\":\"data.dueDate\"}}}");

        assertThat(search.where()).isEqualTo(
            "exists (select 1 from jsonb_array_elements(jsonb_path_query_array(u.data, ?::jsonpath)) as e(value) "
                + "where e.value is not null and e.value <> 'null'::jsonb)");
        assertThat(search.whereParams()).containsExactly("lax $.\"dueDate\"");
    }

    @Test
    void collectionPathsResolveThroughTheirElements() {
        var search = translate(
            "{\"query\":{\"term\":{\"data.CaseAccessGroups.value.caseAccessGroupId.keyword\":\"abc\"}}}");

        assertThat(search.whereParams())
            .containsExactly("lax $.\"CaseAccessGroups\".\"value\".\"caseAccessGroupId\"", "abc");
    }

    @Test
    void mustNotIsNegated() {
        assertThat(translate("{\"query\":{\"bool\":{\"must_not\":[{\"match_all\":{}}]}}}").where())
            .isEqualTo("(not coalesce(true, false))");
    }

    @Test
    void mustNotKeepsRowsWhoseMetadataColumnIsNull() {
        // A null column makes the predicate null, and elasticsearch returns a document whose
        // field is missing, so the negation has to treat null as "did not match".
        var query = "{\"query\":{\"bool\":{\"must_not\":[{\"range\":"
            + "{\"last_state_modified_date\":{\"gte\":\"2024-01-01\"}}}]}}}";

        assertThat(translate(query).where()).startsWith("(not coalesce(");
    }

    @Test
    void shouldDefaultsToOneWhenThereIsNoMustOrFilter() {
        assertThat(translate(
            "{\"query\":{\"bool\":{\"should\":[{\"match_all\":{}},{\"match_none\":{}}]}}}").where())
            .isEqualTo("((true or false))");
    }

    @Test
    void shouldIsOptionalAlongsideAMust() {
        assertThat(translate("{\"query\":{\"bool\":{\"must\":[{\"match_none\":{}}],"
            + "\"should\":[{\"match_all\":{}}]}}}").where())
            .isEqualTo("(false)");
    }

    @Test
    void minimumShouldMatchIsHonoured() {
        assertThat(translate("{\"query\":{\"bool\":{\"minimum_should_match\":\"2\","
            + "\"should\":[{\"match_all\":{}},{\"match_none\":{}},{\"match_all\":{}}]}}}").where())
            .isEqualTo("(((case when true then 1 else 0 end) + (case when false then 1 else 0 end)"
                + " + (case when true then 1 else 0 end)) >= 2)");
    }

    @Test
    void minimumShouldMatchIsIgnoredWithoutShouldClauses() {
        // The cross case type security wrapper sets it even when it adds no should clauses.
        assertThat(translate("{\"query\":{\"bool\":{\"must\":[{\"match_all\":{}}],"
            + "\"minimum_should_match\":\"1\"}}}").where())
            .isEqualTo("(true)");
    }

    @Test
    void minimumShouldMatchLargerThanTheShouldCountMatchesNothing() {
        assertThat(translate("{\"query\":{\"bool\":{\"minimum_should_match\":5,"
            + "\"should\":[{\"match_all\":{}}]}}}").where())
            .isEqualTo("(false)");
    }

    @Test
    void wrapperQueriesAreBase64Decoded() {
        var inner = "{\"bool\":{\"must\":[{\"term\":{\"state.keyword\":\"Draft\"}}]}}";
        var encoded = Base64.getEncoder().encodeToString(inner.getBytes(StandardCharsets.UTF_8));

        var search = translate("{\"query\":{\"bool\":{\"must\":[{\"wrapper\":{\"query\":\"" + encoded + "\"}}]}}}");

        assertThat(search.where()).isEqualTo("((u.state = ?))");
        assertThat(search.whereParams()).containsExactly("Draft");
    }

    @Test
    void nestedMatchesWithinOneCollectionElement() {
        // Global search's party query, which must match one party rather than any combination.
        var search = translate("""
            {"query":{"nested":{"path":"data.SearchCriteria.SearchParties.value","score_mode":"sum",
            "query":{"bool":{"must":[
              {"wildcard":{"data.SearchCriteria.SearchParties.value.Name.keyword":{"wildcard":"Smith*"}}},
              {"match_phrase":{"data.SearchCriteria.SearchParties.value.PostCode":
                 {"query":"SW1A 1AA","analyzer":"standard"}}}
            ]}}}}}
            """);

        assertThat(search.where()).startsWith(
            "exists (select 1 from jsonb_array_elements(jsonb_path_query_array(u.data, ?::jsonpath)) as n0(elem)");
        assertThat(search.whereParams()).containsExactly(
            "lax $.\"SearchCriteria\".\"SearchParties\".\"value\"",
            "lax $.\"Name\"",
            "Smith%",
            "lax $.\"PostCode\"",
            " sw1a 1aa ");
        assertThat(search.where()).contains("jsonb_path_query_array(n0.elem, ?::jsonpath)");
    }

    @Test
    void nestedRejectsFieldsOutsideItsPath() {
        assertThatThrownBy(() -> translate("{\"query\":{\"nested\":{\"path\":\"data.SearchCriteria.SearchParties"
            + ".value\",\"query\":{\"term\":{\"state\":\"open\"}}}}}"))
            .isInstanceOf(EsStubException.class)
            .hasMessageContaining("field 'state'")
            .hasMessageContaining("nested path");
    }

    @Test
    void sortAcceptsEveryFormCcdSends() {
        // XUI sends {field: 'DESC'}, the data store appends the bare 'created_date' column, and
        // global search sends {field: {order: 'desc'}}.
        var search = translate("{\"sort\":[{\"data.caseName.keyword\":\"DESC\"},"
            + "{\"reference.keyword\":{\"order\":\"asc\"}},\"created_date\",\"_score\"]}");

        assertThat(search.sorts()).hasSize(3);
        assertThat(search.sorts().get(0).expression())
            .isEqualTo("(jsonb_path_query_array(u.data, ?::jsonpath) ->> 0)");
        assertThat(search.sorts().get(0).params()).containsExactly("lax $.\"caseName\"");
        assertThat(search.sorts().get(0).ascending()).isFalse();
        assertThat(search.sorts().get(1).expression()).isEqualTo("u.reference");
        assertThat(search.sorts().get(1).ascending()).isTrue();
        assertThat(search.sorts().get(2).expression()).isEqualTo("u.created_date");
    }

    @Test
    void anEmptySortObjectIsAscending() {
        // co.elastic.clients rewrites the bare 'created_date' the sort service appends to this.
        var search = translate("{\"sort\":[{\"created_date\":{}}]}");

        assertThat(search.sorts()).hasSize(1);
        assertThat(search.sorts().get(0).expression()).isEqualTo("u.created_date");
        assertThat(search.sorts().get(0).ascending()).isTrue();
    }

    @Test
    void clauseLevelBoostIsIgnored() {
        // Every leaf org.elasticsearch serialises carries a default boost beside the field name.
        var search = translate("{\"query\":{\"terms\":{\"state\":[\"draft\"],\"boost\":1.0}}}");

        assertThat(search.whereParams()).containsExactly("draft");
    }

    @Test
    void sourceIncludesAreReadFromBothFormsCcdSends() {
        assertThat(translate("{\"_source\":[\"reference\",\"data\"]}").sourceIncludes())
            .containsExactly("reference", "data");
        assertThat(translate("{\"_source\":{\"includes\":[\"reference\"]}}").sourceIncludes())
            .containsExactly("reference");
        assertThat(translate("{\"_source\":false}").sourceIncludes()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"aggs", "aggregations", "collapse", "post_filter", "indices_boost", "highlight",
        "search_after", "script_fields", "min_score", "suggest", "runtime_mappings"})
    void unsupportedTopLevelKeysAreRejected(String key) {
        var thrown = catchIt("{\"" + key + "\":{}}");

        assertThat(thrown)
            .isInstanceOf(EsStubException.class)
            .hasMessageContaining("does not support '" + key + "'")
            .hasMessageContaining("top level");
        assertThat(((EsStubException) thrown).type()).isEqualTo(EsStubException.UNSUPPORTED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"query_string", "simple_query_string", "multi_match", "function_score", "script",
        "script_score", "more_like_this", "fuzzy", "prefix", "ids", "constant_score", "dis_max", "geo_distance",
        "span_near", "percolate", "knn"})
    void unsupportedQueryClausesAreRejected(String clause) {
        var thrown = catchIt("{\"query\":{\"" + clause + "\":{}}}");

        assertThat(thrown).isInstanceOf(EsStubException.class);
        assertThat(thrown).hasMessageContaining("does not support '" + clause + "'");
        assertThat(thrown).hasMessageContaining("query clause");
        assertThat(((EsStubException) thrown).status()).isEqualTo(400);
    }

    @Test
    void unsupportedBoolKeysAreRejected() {
        assertThat(catchIt("{\"query\":{\"bool\":{\"filter_path\":[]}}}"))
            .hasMessageContaining("does not support 'filter_path'")
            .hasMessageContaining("bool query");
    }

    @Test
    void unknownFieldsAreRejected() {
        assertThat(catchIt("{\"query\":{\"term\":{\"index_id\":\"nfd_cases\"}}}"))
            .isInstanceOf(EsStubException.class)
            .hasMessageContaining("field 'index_id'");
    }

    @Test
    void fuzzinessIsRejectedBecauseItWouldChangeWhichCasesMatch() {
        assertThat(catchIt("{\"query\":{\"match\":{\"data.name\":{\"query\":\"smith\",\"fuzziness\":\"2\"}}}}"))
            .hasMessageContaining("does not support 'fuzziness'");
    }

    @Test
    void phraseSlopIsRejected() {
        assertThat(catchIt("{\"query\":{\"match_phrase\":{\"data.name\":{\"query\":\"a b\",\"slop\":2}}}}"))
            .hasMessageContaining("does not support 'slop'");
    }

    @Test
    void nestedInsideNestedIsRejected() {
        assertThat(catchIt("{\"query\":{\"nested\":{\"path\":\"data.a.value\",\"query\":"
            + "{\"nested\":{\"path\":\"data.a.value.b.value\",\"query\":{\"match_all\":{}}}}}}}"))
            .hasMessageContaining("nested inside nested");
    }

    @Test
    void nestedOnACollectionRatherThanItsElementIsRejected() {
        // Addressing the collection would let separate fields match in separate elements, which
        // is the opposite of what nesting means.
        assertThat(catchIt("{\"query\":{\"nested\":{\"path\":\"data.SearchCriteria.SearchParties\","
            + "\"query\":{\"match_all\":{}}}}}"))
            .hasMessageContaining("ending in '.value'");
    }

    @Test
    void nestedOnAMetadataColumnIsRejected() {
        assertThat(catchIt("{\"query\":{\"nested\":{\"path\":\"state\",\"query\":{\"match_all\":{}}}}}"))
            .hasMessageContaining("nested on 'state'");
    }

    @Test
    void termsLookupIsRejected() {
        assertThat(catchIt("{\"query\":{\"terms\":{\"state\":{\"index\":\"other\",\"id\":\"1\"}}}}"))
            .hasMessageContaining("Only an inline array of values is supported");
    }

    @Test
    void sourceExcludesAreRejected() {
        assertThat(catchIt("{\"_source\":{\"excludes\":[\"data\"]}}"))
            .hasMessageContaining("does not support '_source.excludes'");
    }

    @Test
    void sortModesAreRejected() {
        assertThat(catchIt("{\"sort\":[{\"data.a\":{\"order\":\"asc\",\"mode\":\"min\"}}]}"))
            .hasMessageContaining("does not support 'mode'");
    }

    @Test
    void clauseWithSeveralKeysIsRejected() {
        assertThat(catchIt("{\"query\":{\"match_all\":{},\"term\":{\"state\":\"a\"}}}"))
            .hasMessageContaining("exactly one key");
    }

    @Test
    void negativeFromIsRejected() {
        assertThat(catchIt("{\"from\":-1}")).hasMessageContaining("from must be a non-negative whole number");
    }

    @Test
    void unsupportedClausesNeverMentionTheIndexWarmUpErrors() {
        // The acceptance suite retries on these two error strings, so using them for a real
        // failure would make it spin until it times out instead of failing clearly.
        var thrown = (EsStubException) catchIt("{\"query\":{\"multi_match\":{}}}");

        assertThat(thrown.type()).doesNotContain("index_not_found_exception");
        assertThat(thrown.getMessage()).doesNotContain("index_not_found_exception");
        assertThat(thrown.getMessage()).doesNotContain("no_shard_available_action_exception");
    }

    private Throwable catchIt(String json) {
        try {
            translate(json);
        } catch (Throwable t) {
            return t;
        }
        throw new AssertionError("Expected " + json + " to be rejected");
    }
}
