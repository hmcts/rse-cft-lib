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

    /**
     * A query value bound for postgres to lowercase, which is where every fold happens: the
     * stored value and the value searched for have to be folded by the same engine.
     */
    private static final String NEEDLE = "lower(?::text)";

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

        assertThat(search.where()).isEqualTo(NEEDLE + " = any(" + tokens("u.state") + ")");
        assertThat(search.whereParams()).containsExactly("open");
    }

    @Test
    void termOnAnalysedMetadataIsFoldedByPostgresRatherThanTheTranslator() {
        // Elasticsearch indexes 'Draft' as the token 'draft', so a term query on the text field
        // matches regardless of the stored case. The value is bound as it came and lowercased by
        // the same engine, and under the same locale, as the column it is compared with.
        var search = translate("{\"query\":{\"term\":{\"state\":{\"value\":\"Draft\",\"boost\":1.0}}}}");

        assertThat(search.where()).startsWith(NEEDLE + " =");
        assertThat(search.whereParams()).containsExactly("Draft");
    }

    @Test
    void termOnKeywordSubfieldMatchesTheWholeValueFolded() {
        // CCD gives every keyword sub-field it maps the lowercase_normalizer, so the sub-field
        // holds 'draft' however the case was stored and whatever case the query asks for.
        var search = translate("{\"query\":{\"term\":{\"state.keyword\":\"Draft\"}}}");

        assertThat(search.where()).isEqualTo(normalised("u.state") + " = " + normalised("?::text"));
        assertThat(search.whereParams()).containsExactly("Draft");
    }

    @Test
    void keywordMatchingFoldsAccents() {
        // asciifolding is the normalizer's second filter: a search for 'Beiro' finds 'Béiró'.
        var search = translate("{\"query\":{\"term\":{\"data.partyName.keyword\":\"Béiró\"}}}");

        assertThat(search.where()).contains(normalised("?::text"));
        assertThat(search.whereParams()).containsExactly("lax $.\"partyName\"", "Béiró");
    }

    @Test
    void securityClassificationIsMatchedWholeAndFolded() {
        var search = translate("{\"query\":{\"terms\":{\"security_classification\":[\"PUBLIC\",\"PRIVATE\"]}}}");

        var folded = normalised("u.security_classification") + " = " + normalised("?::text");
        assertThat(search.where()).isEqualTo("(" + folded + " or " + folded + ")");
        assertThat(search.whereParams()).containsExactly("PUBLIC", "PRIVATE");
    }

    @Test
    void supplementaryDataKeywordsAreNotFoldedBecauseCcdDoesNotMapThem() {
        // Elasticsearch's dynamic mapping gives supplementary_data a plain keyword sub-field,
        // with no normalizer, so it is matched as stored.
        var search = translate("{\"query\":{\"term\":{\"supplementary_data.HMCTSServiceId.keyword\":\"AAA6\"}}}");

        assertThat(search.where()).contains("v.val = ?").doesNotContain("translate(");
        assertThat(search.whereParams()).containsExactly("lax $.\"HMCTSServiceId\"", "AAA6");
    }

    @Test
    void dataClassificationIsMatchedWholeBecauseItIsMappedAsAPlainKeyword() {
        var search = translate("{\"query\":{\"term\":{\"data_classification.partyName\":\"PUBLIC\"}}}");

        assertThat(search.where()).contains("v.val = ?").doesNotContain("regexp_split_to_array");
        assertThat(search.whereParams()).containsExactly("lax $.\"partyName\"", "PUBLIC");
    }

    @Test
    void termsOnReferenceCastsTheColumn() {
        var search = translate("{\"query\":{\"terms\":{\"reference.keyword\":[1234567890123456]}}}");

        assertThat(search.where()).isEqualTo("(u.reference::text = ?)");
        assertThat(search.whereParams()).containsExactly("1234567890123456");
    }

    @Test
    void referenceIgnoresTheHyphensTheUiPutsInIt() {
        // CCD gives reference a search analyzer that strips hyphens and does not split the
        // value, so the grouped form a user copies out of the UI finds the case.
        var search = translate("{\"query\":{\"match\":{\"reference\":\"1234-5678-9012-3456\"}}}");

        assertThat(search.where()).isEqualTo("u.reference::text = ?");
        assertThat(search.whereParams()).containsExactly("1234567890123456");
    }

    @Test
    void referenceDoesNotMatchOneGroupOfItsDigits() {
        // The search analyzer's keyword tokenizer produces one term, so a part of a reference is
        // not a term of it.
        var search = translate("{\"query\":{\"term\":{\"reference\":\"1234\"}}}");

        assertThat(search.where()).isEqualTo("u.reference::text = ?");
        assertThat(search.whereParams()).containsExactly("1234");
    }

    @Test
    void referencePrefixDropsHyphensToo() {
        var search = translate("{\"query\":{\"match_phrase_prefix\":{\"reference\":\"1234-56\"}}}");

        assertThat(search.where()).isEqualTo("u.reference::text like ?::text escape '\\'");
        assertThat(search.whereParams()).containsExactly("123456%");
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

        var token = "exists (select 1 from jsonb_array_elements_text(jsonb_path_query_array(u.data, ?::jsonpath)) "
            + "as v(val) where " + NEEDLE + " = any(" + tokens("v.val") + "))";
        assertThat(search.where()).isEqualTo("(" + token + " and " + token + ")");
        assertThat(search.whereParams())
            .containsExactly("lax $.\"applicant1FirstName\"", "Ann", "lax $.\"applicant1FirstName\"", "Marie");
    }

    @Test
    void anApostropheDoesNotSplitAName() {
        // The standard tokenizer keeps o'brien whole. The stub drops the apostrophe instead of
        // keeping it, which matches the same cases because it drops it from the query too.
        var search = translate("{\"query\":{\"match\":{\"data.lastName\":\"O'Brien\"}}}");

        assertThat(search.whereParams()).containsExactly("lax $.\"lastName\"", "OBrien");
    }

    @Test
    void anEmailSplitsOnlyAtTheAtSign() {
        var search = translate(
            "{\"query\":{\"match\":{\"data.email\":{\"operator\":\"and\",\"query\":\"john.smith@example.com\"}}}}");

        assertThat(search.whereParams())
            .containsExactly("lax $.\"email\"", "johnsmith", "lax $.\"email\"", "examplecom");
    }

    @Test
    void trailingPunctuationIsNotPartOfAToken() {
        assertThat(EsQueryTranslator.tokenise("Smith, J.")).containsExactly("Smith", "J");
    }

    @Test
    void everyJoinerInAValueIsSquashed() {
        // Squashing one joiner must not step over the next: an address held as '100' and '01'
        // could not be found by the address itself.
        assertThat(EsQueryTranslator.tokenise("10.0.0.1")).containsExactly("10001");
        assertThat(EsQueryTranslator.tokenise("J.R.R. Tolkien")).containsExactly("JRR", "Tolkien");
    }

    @Test
    void commaJoinsDigitsAndSeparatesWords() {
        // Elasticsearch's word boundary rules join a comma between two digits and nowhere else,
        // so a name typed surname first is two words and 'Joe' finds it.
        assertThat(EsQueryTranslator.tokenise("1,234.50")).containsExactly("123450");
        assertThat(EsQueryTranslator.tokenise("Bloggs,Joe")).containsExactly("Bloggs", "Joe");
    }

    @Test
    void dotOrApostropheJoinsOnlyLikeWithLike() {
        // A dot joins two letters or two digits, and the apostrophe only letters, as the
        // boundary rules have it.
        assertThat(EsQueryTranslator.tokenise("john.smith")).containsExactly("johnsmith");
        assertThat(EsQueryTranslator.tokenise("1.5")).containsExactly("15");
        assertThat(EsQueryTranslator.tokenise("a.1")).containsExactly("a", "1");
        assertThat(EsQueryTranslator.tokenise("1'2")).containsExactly("1", "2");
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

        assertThat(search.where()).contains(
            "strpos((' ' || array_to_string(" + tokens("v.val") + ", ' ') || ' '), " + NEEDLE + ") > 0");
        assertThat(search.whereParams()).containsExactly("lax $.\"name\"", " Ann Marie Smith ");
    }

    @Test
    void matchPhrasePrefixOnCaseAccessCategory() {
        // What the grant type builders emit for case access categories.
        var search = translate(
            "{\"query\":{\"match_phrase_prefix\":{\"data.CaseAccessCategory\":\"Civil/Standard\"}}}");

        assertThat(search.where()).contains(" ~ " + NEEDLE);
        assertThat(search.whereParams())
            .containsExactly("lax $.\"CaseAccessCategory\"", " Civil Standard[^ ]* ");
    }

    @Test
    void wildcardOnAKeywordFieldIsTranslatedToLike() {
        // Global search's case reference and party searches.
        var search = translate("{\"query\":{\"wildcard\":{\"reference.keyword\":{\"wildcard\":\"16*\"}}}}");

        assertThat(search.where()).isEqualTo("u.reference::text like ?::text escape '\\'");
        assertThat(search.whereParams()).containsExactly("16%");
    }

    @Test
    void wildcardOnAnAnalysedFieldMatchesTokens() {
        // XUI sends '*term*' lowercased for its configured wildcard search fields.
        var search = translate("{\"query\":{\"wildcard\":{\"data.caseName\":\"*smith*\"}}}");

        assertThat(search.where()).contains("unnest(" + tokens("v.val") + ")");
        assertThat(search.whereParams()).containsExactly("lax $.\"caseName\"", "%smith%");
    }

    @Test
    void wildcardOnAnAnalysedFieldDropsTheJoinersItsTokensHaveLost() {
        // The tokens hold obrien, so a pattern still carrying the apostrophe would match none
        // of them.
        var search = translate("{\"query\":{\"wildcard\":{\"data.caseName\":\"*o'brie*\"}}}");

        assertThat(search.whereParams()).containsExactly("lax $.\"caseName\"", "%obrie%");
    }

    @Test
    void wildcardDropsAJoinerNextToAWildcard() {
        // The tokens hold johnsmith, so a pattern whose dot survived would match none of them.
        // XUI wraps what the user typed in stars, which is how the dot ends up beside one.
        var search = translate("{\"query\":{\"wildcard\":{\"data.email\":\"*john.*\"}}}");

        assertThat(search.whereParams()).containsExactly("lax $.\"email\"", "%john%");
    }

    @Test
    void wildcardOnAKeywordSubfieldIsFolded() {
        var search = translate("{\"query\":{\"wildcard\":{\"data.caseName.keyword\":{\"wildcard\":\"Smith*\"}}}}");

        assertThat(search.where()).contains(normalised("v.val") + " like " + normalised("?::text"));
        assertThat(search.whereParams()).containsExactly("lax $.\"caseName\"", "Smith%");
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
    void anUpperDateBoundCoversTheWholeOfItsDay() {
        // Elasticsearch rounds a bound with no time of day to the end of that day, so a case
        // created at noon on the 31st is inside a range that ends on the 31st.
        var search = translate("{\"query\":{\"range\":{\"created_date\":"
            + "{\"gte\":\"2024-01-01\",\"lte\":\"2024-01-31\"}}}}");

        assertThat(search.whereParams()).containsExactly("2024-01-01", "2024-01-31T23:59:59.999");
    }

    @Test
    void greaterThanBoundSkipsTheWholeOfItsDay() {
        var search = translate("{\"query\":{\"range\":{\"last_modified\":"
            + "{\"gt\":\"2024-01-01\",\"lt\":\"2024-01-31\"}}}}");

        assertThat(search.whereParams()).containsExactly("2024-01-01T23:59:59.999", "2024-01-31");
    }

    @Test
    void boundThatNamesATimeIsLeftAlone() {
        var search = translate("{\"query\":{\"range\":{\"created_date\":{\"lte\":\"2024-01-31T09:30:00.000\"}}}}");

        assertThat(search.whereParams()).containsExactly("2024-01-31T09:30:00.000");
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
    void bindsAValueForEveryPlaceholderAndNoMore() {
        // A should alongside a must is optional, so its sql is discarded; its values must not be
        // left bound, or the statement is given more of them than it has parameters.
        var search = translate("{\"query\":{\"bool\":{\"must\":[{\"match\":{\"state\":\"Draft\"}}],"
            + "\"should\":[{\"term\":{\"jurisdiction\":\"DIVORCE\"}}]}}}");

        assertThat(search.whereParams())
            .hasSize(EsQueryTranslatorInvariantTest.placeholders(search.where()));
    }

    @Test
    void bindsNothingForShouldsThatCannotAllMatch() {
        var search = translate("{\"query\":{\"bool\":{\"minimum_should_match\":5,"
            + "\"should\":[{\"term\":{\"jurisdiction\":\"DIVORCE\"}}]}}}");

        assertThat(search.where()).isEqualTo("(false)");
        assertThat(search.whereParams()).isEmpty();
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

        assertThat(search.where()).isEqualTo(
            "((" + normalised("u.state") + " = " + normalised("?::text") + "))");
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
            " SW1A 1AA ");
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
            .isEqualTo(normalised("(jsonb_path_query_array(u.data, ?::jsonpath) ->> 0)"));
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

    /** The tokens of an expression, as the standard analyzer and tokenizer leave it. */
    private static String tokens(String expression) {
        var separators = " \\t\\n\\r!-/:-@[-^`{-~";
        var letter = "[^" + separators + "0-9]";
        return "regexp_split_to_array(regexp_replace(lower(" + expression + "), '(?<=" + letter
            + ")[''.](?=" + letter + ")|(?<=[0-9])[.,](?=[0-9])', '', 'g'), '[" + separators + "]+')";
    }

    /** An expression as lowercase_normalizer leaves it: lowercased and ascii folded. */
    private static String normalised(String expression) {
        return "lower(translate(" + expression
            + ", 'àáâãäåèéêëìíîïòóôõöøùúûüýÿñçÀÁÂÃÄÅÈÉÊËÌÍÎÏÒÓÔÕÖØÙÚÛÜÝÑÇ'"
            + ", 'aaaaaaeeeeiiiioooooouuuuyyncAAAAAAEEEEIIIIOOOOOOUUUUYNC'))";
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
