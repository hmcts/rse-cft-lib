package uk.gov.hmcts.rse.ccd.lib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Base64;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Drives the stub over HTTP with the query bodies the data store really sends, against a real
 * postgres holding the centralised case_data table.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EsStubTest {

    private static final String CASE_TYPE = "NFD";
    private static final String CASES_INDEX = "nfd_cases";

    private static EmbeddedPostgres postgres;
    private static EsStub stub;
    private static String baseUrl;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeAll
    void startStack() throws Exception {
        // As cftlib's own wrapper configures it: the encoding decides whether an accented
        // character is one character or a string of bytes to every expression below.
        postgres = EmbeddedPostgres.builder()
            .setLocaleConfig("encoding", "UTF8")
            .setLocaleConfig("lc-collate", "C")
            .setLocaleConfig("lc-ctype", "C")
            .start();
        try (var c = connect("postgres"); var s = c.createStatement()) {
            s.execute("create database datastore");
        }
        createSchema();
        insertCases();

        stub = new EsStub(new EsStubSources("datastore", EsStubTest::connect));
        stub.bind("localhost", 0);
        baseUrl = "http://localhost:" + stub.port();
    }

    @AfterAll
    void stopStack() throws Exception {
        if (stub != null) {
            stub.stop();
        }
        if (postgres != null) {
            postgres.close();
        }
    }

    private static Connection connect(String database) throws SQLException {
        return DriverManager.getConnection(
            postgres.getJdbcUrl("postgres", database) + "&stringtype=unspecified", "postgres", "postgres");
    }

    /**
     * The columns of the data store's case_data table, from its base flyway migration.
     */
    private void createSchema() throws Exception {
        try (var c = connect("datastore"); var s = c.createStatement()) {
            s.execute("create type securityclassification as enum ('PUBLIC', 'PRIVATE', 'RESTRICTED')");
            s.execute("""
                create table case_data (
                    id bigserial primary key,
                    created_date timestamp without time zone default now() not null,
                    last_modified timestamp without time zone,
                    jurisdiction varchar(255) not null,
                    case_type_id varchar(255) not null,
                    state varchar(255) not null,
                    data jsonb not null,
                    data_classification jsonb,
                    reference bigint not null,
                    security_classification securityclassification not null,
                    version integer default 1,
                    last_state_modified_date timestamp without time zone,
                    supplementary_data jsonb,
                    marked_by_logstash boolean default false
                )
                """);
        }
    }

    private void insertCases() throws Exception {
        insert(1234567890123456L, "Draft", "PUBLIC", "2024-01-03 10:00:00", """
            {
              "applicant1FirstName": "Ann-Marie",
              "applicant1LastName": "Smith",
              "applicant2LastName": "O'Béiró",
              "solicitorEmail": "john.smith@example.com",
              "CaseAccessCategory": "Civil/Standard",
              "caseNameHmctsInternal": "Smith v Jones",
              "SearchCriteria": {
                "OtherCaseReferences": [{"id": "1", "value": "REF-001"}],
                "SearchParties": [
                  {"id": "1", "value":
                    {"Name": "Ann-Marie Smith", "PostCode": "SW1A 1AA", "DateOfBirth": "1990-01-01"}},
                  {"id": "2", "value": {"Name": "Bob Jones", "PostCode": "M1 1AA"}}
                ]
              }
            }
            """);
        insert(1234567890123457L, "Submitted", "PRIVATE", "2024-01-01 10:00:00", """
            {
              "applicant1FirstName": "Bob",
              "applicant1LastName": "Jones",
              "caseNameHmctsInternal": "Jones v Brown"
            }
            """);
        insert(1234567890123458L, "Draft", "PUBLIC", "2024-01-02 10:00:00", """
            {
              "applicant1FirstName": "Carol",
              "applicant1LastName": "Smith",
              "SearchCriteria": {"SearchParties": [{"id": "1", "value": {"Name": "Carol Smith"}}]}
            }
            """);
    }

    private void insert(long reference, String state, String classification, String created, String data)
        throws Exception {
        try (var c = connect("datastore");
             var s = c.prepareStatement("""
                 insert into case_data(created_date, last_modified, jurisdiction, case_type_id, state, data,
                     data_classification, reference, security_classification, supplementary_data)
                 values (?::timestamp, ?::timestamp, 'DIVORCE', ?, ?, ?::jsonb,
                     '{"applicant1FirstName": "PUBLIC"}'::jsonb, ?, ?, '{"HMCTSServiceId": "AAA1"}'::jsonb)
                 """)) {
            s.setString(1, created);
            s.setString(2, created);
            s.setString(3, CASE_TYPE);
            s.setString(4, state);
            s.setString(5, data);
            s.setLong(6, reference);
            s.setString(7, classification);
            s.execute();
        }
    }

    @Test
    void oneMalformedSearchDoesNotDenyTheOthersTheirResults() throws Exception {
        var good = "{\"index\":[\"" + CASES_INDEX + "\"]}\n{\"query\":{\"match_all\":{}}}\n";
        var truncated = "{\"index\":[\"" + CASES_INDEX + "\"]}\n{\"query\":{\"match_all\":\n";

        var response = post("/_msearch", good + truncated + good);

        assertThat(response.statusCode()).isEqualTo(200);
        var items = mapper.readTree(response.body()).at("/responses");
        assertThat(items).hasSize(3);
        assertThat(items.get(0).at("/hits/hits")).isNotEmpty();
        assertThat(items.get(1).at("/error/reason").asText()).contains("not json");
        assertThat(items.get(2).at("/hits/hits")).isNotEmpty();
    }

    @Test
    void reportsTheTotalForACountOnlySearch() throws Exception {
        var response = post("/" + CASES_INDEX + "/_search", "{\"size\":0,\"query\":{\"match_all\":{}}}");

        var result = mapper.readTree(response.body());
        assertThat(result.at("/hits/hits")).isEmpty();
        assertThat(result.at("/hits/total/value").asLong()).isEqualTo(3);
    }

    @Test
    void returnsNoDocumentWhenTheSourceIsTurnedOff() throws Exception {
        var response = post("/" + CASES_INDEX + "/_search", "{\"_source\":false,\"query\":{\"match_all\":{}}}");

        var hits = mapper.readTree(response.body()).at("/hits/hits");
        assertThat(hits).isNotEmpty();
        hits.forEach(hit -> assertThat(hit.has("_source")).isFalse());
    }

    @Test
    void rejectsASearchOverSeveralIndices() throws Exception {
        var response = post("/" + CASES_INDEX + "," + CASES_INDEX + "/_search",
            "{\"query\":{\"match_all\":{}}}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("several indices");
    }

    @Test
    void refusesAPortHeldBySomethingElseRatherThanAnsweringFromIt() throws Exception {
        try (var occupied = new java.net.ServerSocket(0)) {
            var port = occupied.getLocalPort();
            // An elasticsearch cftlib did not start would never be populated, since this mode
            // runs no indexer, so waiting on a document in it could not succeed.
            assertThatThrownBy(() -> new EsStub().start("localhost", port))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("already in use")
                .hasMessageContaining("RSE_LIB_ES=external");
        }
    }

    @Test
    void fetchesAnIndexedDocumentById() throws Exception {
        var response = get("/" + CASES_INDEX + "/_doc/" + caseDataId(1234567890123456L));

        assertThat(response.statusCode()).isEqualTo(200);
        var document = mapper.readTree(response.body());
        assertThat(document.get("found").asBoolean()).isTrue();
        assertThat(document.at("/_source/data/applicant1FirstName").asText()).isEqualTo("Ann-Marie");
        assertThat(response.headers().firstValue("X-Elastic-Product")).hasValue("Elasticsearch");
    }

    @Test
    void reportsAnAbsentDocumentAsNotFound() throws Exception {
        var response = get("/" + CASES_INDEX + "/_doc/99999999");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(mapper.readTree(response.body()).get("found").asBoolean()).isFalse();
    }

    private long caseDataId(long reference) throws Exception {
        try (var c = connect("datastore");
             var s = c.prepareStatement("select id from case_data where reference = ?")) {
            s.setLong(1, reference);
            try (var rs = s.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    @Test
    void healthIsGreenSoThatTheReadinessProbePasses() throws Exception {
        var response = get("/_cat/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("green");
        assertThat(response.headers().firstValue("X-Elastic-Product")).hasValue("Elasticsearch");
    }

    @Test
    void rootReportsAVersion() throws Exception {
        var response = get("/");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(response.body()).at("/version/number").asText()).isNotBlank();
        assertThat(response.headers().firstValue("X-Elastic-Product")).hasValue("Elasticsearch");
    }

    @Test
    void anUnknownPathNamesTheStub() throws Exception {
        var response = get("/nfd_cases/_mapping");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("cftlib elasticsearch stub");
        assertThat(response.headers().firstValue("X-Elastic-Product")).hasValue("Elasticsearch");
    }

    @Test
    void caseListReturnsEveryCaseAndCarriesTheProductHeader() throws Exception {
        var response = msearch(CASES_INDEX, caseListBody("{\"bool\":{\"must\":[]}}", "[]"));

        assertThat(response.headers().firstValue("X-Elastic-Product")).hasValue("Elasticsearch");
        var item = firstItem(response);
        assertThat(item.at("/status").asInt()).isEqualTo(200);
        assertThat(item.at("/hits/total/value").asInt()).isEqualTo(3);
        assertThat(references(item)).containsExactlyInAnyOrder(
            "1234567890123456", "1234567890123457", "1234567890123458");
        assertThat(item.at("/hits/hits/0/_index").asText()).isEqualTo(CASES_INDEX);
    }

    @Test
    void theDocumentHasTheShapeTheIndexerWouldHaveWritten() throws Exception {
        var item = firstItem(msearch(CASES_INDEX, caseListBody("{\"match_all\":{}}", "[]")));
        var source = item.at("/hits/hits/0/_source");

        assertThat(source.fieldNames()).toIterable().containsExactlyInAnyOrder(
            "reference", "jurisdiction", "case_type_id", "state", "created_date", "last_modified",
            "last_state_modified_date", "security_classification", "data", "data_classification");
        assertThat(source.at("/jurisdiction").asText()).isEqualTo("DIVORCE");
        assertThat(source.at("/case_type_id").asText()).isEqualTo(CASE_TYPE);
        assertThat(source.at("/data/applicant1LastName").asText()).isNotBlank();
        // The metadata CCD asks for omits the document id, so a real elasticsearch drops it too.
        assertThat(source.has("id")).isFalse();
        assertThat(source.has("@timestamp")).isFalse();
    }

    @Test
    void supplementaryDataIsReturnedWhenAskedFor() throws Exception {
        var body = "{\"from\":0,\"size\":25,\"query\":{\"match_all\":{}},\"_source\":"
            + "[\"reference\",\"data\",\"supplementary_data.*\"]}";
        var item = firstItem(msearch(CASES_INDEX, body));

        assertThat(item.at("/hits/hits/0/_source/supplementary_data/HMCTSServiceId").asText()).isEqualTo("AAA1");
    }

    @Test
    void matchOnACaseDataFieldAndsItsTokens() throws Exception {
        var query = "{\"bool\":{\"must\":[{\"match\":{\"data.applicant1FirstName\":"
            + "{\"operator\":\"and\",\"query\":\"ann marie\"}}}]}}";
        var item = firstItem(msearch(CASES_INDEX, caseListBody(query, "[]")));

        assertThat(references(item)).containsExactly("1234567890123456");
    }

    @Test
    void matchIsCaseInsensitiveOnAnalysedFieldsAsElasticsearchIs() throws Exception {
        // The case state filter lowercases its values; the column holds 'Draft'.
        var query = "{\"bool\":{\"must\":[{\"terms\":{\"state\":[\"draft\"]}}]}}";
        var item = firstItem(msearch(CASES_INDEX, caseListBody(query, "[]")));

        assertThat(references(item)).containsExactlyInAnyOrder("1234567890123456", "1234567890123458");
    }

    /**
     * Every clause has to find a value by the value itself. The query is analysed partly in java
     * and the stored value entirely in postgres, so a disagreement between the two — over where a
     * token ends, or over how a character lowercases under the cluster's locale — would leave a
     * case unfindable by the very text it holds, on some machines and not others.
     */
    @ParameterizedTest
    @ValueSource(strings = {"O'Brien", "john.smith@example.com", "Ann-Marie Smith", "José Beiró",
        "CARE_SUPERVISION_EPO", "1,234.50", "Smith, J.", "SW1A 1AA", "a[b]c\\d^e`f{g}h~i",
        "  padded  out  ", "Ωμέγα", "日本語",
        // Joiners two characters apart: squashing one must not step over the next.
        "10.0.0.1", "J.R.R. Tolkien", "a.b.c", "1.2.3.4.5", "Bloggs,Joe"})
    void everyClauseFindsAValueByTheValueItself(String value) throws Exception {
        for (var clause : List.of("match", "match_phrase", "match_phrase_prefix")) {
            assertThat(matchesItself("{\"" + clause + "\":{\"data.name\":%s}}", value))
                .as("%s on '%s'", clause, value)
                .isTrue();
        }
        // A term is a single term, so it finds a value only where the whole of it analyses to
        // one token: elasticsearch holds no term 'ann-marie smith' either.
        assertThat(matchesItself("{\"term\":{\"data.name\":%s}}", value))
            .as("term on '%s'", value)
            .isEqualTo(EsQueryTranslator.tokenise(value).size() == 1);
        assertThat(matchesItself("{\"term\":{\"data.name.keyword\":%s}}", value))
            .as("term on the keyword sub-field of '%s'", value)
            .isTrue();
        assertThat(matchesItself("{\"wildcard\":{\"data.name.keyword\":%s}}", value))
            .as("wildcard on the keyword sub-field of '%s'", value)
            .isTrue();
    }

    /**
     * Runs a query's predicate against a single synthetic row holding the value, which keeps the
     * shared fixture out of it.
     */
    private boolean matchesItself(String clauseTemplate, String value) throws Exception {
        var query = clauseTemplate.formatted(mapper.writeValueAsString(value));
        var search = new EsQueryTranslator(mapper).translate(mapper.readTree("{\"query\":" + query + "}"));
        var sql = "select " + search.where() + " from (select jsonb_build_object('name', ?::text) as data) u";
        try (var c = connect("datastore"); var s = c.prepareStatement(sql)) {
            var index = 1;
            for (var param : search.whereParams()) {
                s.setObject(index++, param);
            }
            s.setString(index, value);
            try (var rs = s.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getBoolean(1);
            }
        }
    }

    @Test
    void termOnAnAnalysedFieldMatchesAValueCarryingAJoiner() throws Exception {
        // A term is not analysed by elasticsearch, but it is looked for among terms that were,
        // where john.smith is one of them. The stub must put the term in the same form.
        var query = "{\"bool\":{\"must\":[{\"term\":{\"data.solicitorEmail\":\"john.smith\"}}]}}";
        var item = firstItem(msearch(CASES_INDEX, caseListBody(query, "[]")));

        assertThat(references(item)).containsExactly("1234567890123456");
    }

    @Test
    void keywordSearchFindsAValueWhoseCaseAndAccentsDiffer() throws Exception {
        // The sub-field holds what lowercase_normalizer leaves: o'beiro, from O'Béiró.
        var query = "{\"bool\":{\"must\":[{\"term\":{\"data.applicant2LastName.keyword\":\"O'Beiro\"}}]}}";
        var item = firstItem(msearch(CASES_INDEX, caseListBody(query, "[]")));

        assertThat(references(item)).containsExactly("1234567890123456");
    }

    @Test
    void anEmailMatchesOnItsLocalPart() throws Exception {
        // The standard tokenizer breaks john.smith@example.com at the @ and nowhere else.
        var query = "{\"bool\":{\"must\":[{\"match\":{\"data.solicitorEmail\":"
            + "{\"operator\":\"and\",\"query\":\"john.smith\"}}}]}}";
        var item = firstItem(msearch(CASES_INDEX, caseListBody(query, "[]")));

        assertThat(references(item)).containsExactly("1234567890123456");
    }

    @Test
    void halfAnEmailsLocalPartIsNotOneOfItsTokens() throws Exception {
        var query = "{\"bool\":{\"must\":[{\"match\":{\"data.solicitorEmail\":\"john\"}}]}}";
        var item = firstItem(msearch(CASES_INDEX, caseListBody(query, "[]")));

        assertThat(references(item)).isEmpty();
    }

    @Test
    void caseReferenceIsFoundInTheGroupedFormTheUiShows() throws Exception {
        var query = "{\"bool\":{\"must\":[{\"match\":{\"reference\":\"1234-5678-9012-3456\"}}]}}";
        var item = firstItem(msearch(CASES_INDEX, caseListBody(query, "[]")));

        assertThat(references(item)).containsExactly("1234567890123456");
    }

    @Test
    void anUpperDateBoundCoversCasesCreatedDuringItsDay() throws Exception {
        // The newest case was created at 10:00 on the third, which a range ending on the third
        // includes: elasticsearch rounds a bound with no time of day up to the end of the day.
        var query = "{\"bool\":{\"filter\":[{\"range\":{\"created_date\":{\"lte\":\"2024-01-03\"}}}]}}";
        var item = firstItem(msearch(CASES_INDEX, caseListBody(query, "[]")));

        assertThat(references(item)).hasSize(3);
    }

    @Test
    void securityFilteredSearchWithABase64Wrapper() throws Exception {
        // The shape ElasticsearchCaseSearchRequestSecurity produces: the caller's query wrapped in
        // base64, plus terms filters on classification and state and a lowercased case type term.
        var inner = "{\"bool\":{\"must\":[{\"match\":{\"data.applicant1LastName\":"
            + "{\"operator\":\"and\",\"query\":\"smith\"}}}]}}";
        var encoded = Base64.getEncoder().encodeToString(inner.getBytes(StandardCharsets.UTF_8));
        var query = """
            {"bool":{
              "must":[{"wrapper":{"query":"%s"}}],
              "filter":[
                {"terms":{"security_classification":["PUBLIC"]}},
                {"terms":{"state":["draft","submitted"]}}
              ],
              "should":[{"bool":{"must":[{"term":{"case_type_id":"nfd"}}]}}],
              "minimum_should_match":"1"
            }}
            """.formatted(encoded).replace("\n", "");
        var item = firstItem(msearch(CASES_INDEX, caseListBody(query, "[]")));

        // Carol and Ann-Marie are Smiths; Carol is PUBLIC/Draft and Ann-Marie PUBLIC/Draft, Bob is
        // excluded by name and would also be excluded by his PRIVATE classification.
        assertThat(references(item)).containsExactlyInAnyOrder("1234567890123456", "1234567890123458");
    }

    @Test
    void sortAndPaginationAreApplied() throws Exception {
        var sort = "[{\"created_date\":{\"order\":\"desc\"}}]";
        var page = "{\"from\":1,\"size\":1,\"query\":{\"match_all\":{}},\"sort\":" + sort + "}";
        var item = firstItem(msearch(CASES_INDEX, page));

        assertThat(item.at("/hits/total/value").asInt()).isEqualTo(3);
        assertThat(references(item)).containsExactly("1234567890123458");
    }

    @Test
    void sortOnAKeywordCaseDataField() throws Exception {
        var sort = "[{\"data.applicant1LastName.keyword\":\"ASC\"},\"created_date\"]";
        var item = firstItem(msearch(CASES_INDEX, caseListBody("{\"match_all\":{}}", sort)));

        assertThat(references(item)).containsExactly(
            "1234567890123457", "1234567890123458", "1234567890123456");
    }

    @Test
    void wildcardSearchOnCaseData() throws Exception {
        var query = "{\"bool\":{\"must\":[{\"wildcard\":{\"data.applicant1LastName\":\"*mit*\"}}]}}";
        var item = firstItem(msearch(CASES_INDEX, caseListBody(query, "[]")));

        assertThat(references(item)).containsExactlyInAnyOrder("1234567890123456", "1234567890123458");
    }

    @Test
    void matchPhrasePrefixOnCaseAccessCategory() throws Exception {
        var query = "{\"bool\":{\"must\":[{\"match_phrase_prefix\":{\"data.CaseAccessCategory\":\"Civil\"}}]}}";
        var item = firstItem(msearch(CASES_INDEX, caseListBody(query, "[]")));

        assertThat(references(item)).containsExactly("1234567890123456");
    }

    @Test
    void existsOnACaseDataField() throws Exception {
        var query = "{\"bool\":{\"filter\":[{\"exists\":{\"field\":\"data.CaseAccessCategory\"}}]}}";
        var item = firstItem(msearch(CASES_INDEX, caseListBody(query, "[]")));

        assertThat(references(item)).containsExactly("1234567890123456");
    }

    @Test
    void rangeOnCreatedDate() throws Exception {
        var query = "{\"bool\":{\"filter\":[{\"range\":{\"created_date\":"
            + "{\"gte\":\"2024-01-02\",\"lt\":\"2024-01-03\"}}}]}}";
        var item = firstItem(msearch(CASES_INDEX, caseListBody(query, "[]")));

        assertThat(references(item)).containsExactly("1234567890123458");
    }

    @Test
    void globalSearchOnlyHoldsCasesThatDefineSearchCriteria() throws Exception {
        var item = firstItem(msearch(EsStubSources.GLOBAL_SEARCH_INDEX, globalSearchBody(
            "{\"bool\":{\"must\":[{\"terms\":{\"jurisdiction\":[\"divorce\"]}}],\"minimum_should_match\":\"0\"}}")));

        assertThat(item.at("/hits/total/value").asInt()).isEqualTo(2);
        assertThat(references(item)).containsExactlyInAnyOrder("1234567890123456", "1234567890123458");
    }

    @Test
    void theGlobalSearchDocumentIsFilteredLikeTheIndexersFilterIt() throws Exception {
        var item = firstItem(msearch(EsStubSources.GLOBAL_SEARCH_INDEX,
            globalSearchBody("{\"bool\":{\"must\":[{\"terms\":{\"reference\":[1234567890123456]}}]}}")));
        var source = item.at("/hits/hits/0/_source");

        assertThat(source.at("/data/caseNameHmctsInternal").asText()).isEqualTo("Smith v Jones");
        assertThat(source.at("/data/SearchCriteria/OtherCaseReferences/0/value").asText()).isEqualTo("REF-001");
        assertThat(source.at("/supplementary_data/HMCTSServiceId").asText()).isEqualTo("AAA1");
        // Filtered out of the global search document by the indexers.
        assertThat(source.at("/data").has("applicant1FirstName")).isFalse();
        // Removed from the global search document, and absent from the _source CCD asks for.
        assertThat(source.has("created_date")).isFalse();
        assertThat(source.has("last_modified")).isFalse();
        // Not in the _source global search asks for, even though it is in the document.
        assertThat(source.at("/data/SearchCriteria").has("SearchParties")).isFalse();
    }

    @Test
    void globalSearchPartyQueryMatchesWithinOneParty() throws Exception {
        var matching = "{\"bool\":{\"must\":[{\"bool\":{\"should\":[{\"nested\":{"
            + "\"path\":\"data.SearchCriteria.SearchParties.value\",\"score_mode\":\"sum\",\"query\":{\"bool\":{"
            + "\"must\":["
            + "{\"wildcard\":{\"data.SearchCriteria.SearchParties.value.Name.keyword\":{\"wildcard\":\"Bob*\"}}},"
            + "{\"match_phrase\":{\"data.SearchCriteria.SearchParties.value.PostCode\":"
            + "{\"query\":\"M1 1AA\",\"analyzer\":\"standard\"}}}"
            + "]}}}}],\"minimum_should_match\":\"1\"}}]}}";

        var item = firstItem(msearch(EsStubSources.GLOBAL_SEARCH_INDEX, globalSearchBody(matching)));
        assertThat(references(item)).containsExactly("1234567890123456");

        // Bob's name with Ann-Marie's postcode belongs to no single party, so must not match.
        var crossed = matching.replace("M1 1AA", "SW1A 1AA");
        assertThat(firstItem(msearch(EsStubSources.GLOBAL_SEARCH_INDEX, globalSearchBody(crossed)))
            .at("/hits/total/value").asInt()).isZero();
    }

    @Test
    void anUnsupportedClauseBecomesABadRequestInsideItsOwnMultiSearchSlot() throws Exception {
        var body = header(CASES_INDEX) + "\n{\"query\":{\"match_all\":{}}}\n"
            + header(CASES_INDEX) + "\n{\"query\":{\"query_string\":{\"query\":\"a\"}}}\n";
        var response = msearch(CASES_INDEX, body);
        var items = mapper.readTree(response.body()).at("/responses");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(items.get(0).at("/hits/total/value").asInt()).isEqualTo(3);
        assertThat(items.get(1).at("/status").asInt()).isEqualTo(400);
        assertThat(items.get(1).at("/error/type").asText()).isEqualTo(EsStubException.UNSUPPORTED);
        assertThat(items.get(1).at("/error/reason").asText())
            .contains("does not support 'query_string'");
        assertThat(items.get(1).toString())
            .doesNotContain("index_not_found_exception")
            .doesNotContain("no_shard_available_action_exception");
    }

    @Test
    void anUnsupportedClauseOnASingleSearchIsA400() throws Exception {
        var response = post("/" + CASES_INDEX + "/_search", "{\"aggs\":{\"x\":{\"terms\":{\"field\":\"state\"}}}}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(mapper.readTree(response.body()).at("/error/type").asText())
            .isEqualTo(EsStubException.UNSUPPORTED);
    }

    /**
     * co.elastic.clients rejects a whole response when any of these is missing, so a search would
     * fail as a ServiceException rather than returning anything. Verified against the real
     * MsearchResponse deserializer of elasticsearch-java 9.5.5.
     */
    @Test
    void theResponseEnvelopeCarriesEveryFieldTheElasticsearchClientRequires() throws Exception {
        var response = mapper.readTree(msearch(CASES_INDEX, caseListBody("{\"match_all\":{}}", "[]")).body());

        assertThat(response.get("took").isNumber()).isTrue();
        assertThat(response.get("responses").isArray()).isTrue();

        var item = response.at("/responses/0");
        assertThat(item.get("status").asInt()).isEqualTo(200);
        assertThat(item.get("took").isNumber()).isTrue();
        assertThat(item.get("timed_out").isBoolean()).isTrue();
        assertThat(item.at("/_shards/successful").asInt()).isEqualTo(1);
        assertThat(item.at("/hits/total/value").asInt()).isEqualTo(3);
        assertThat(item.at("/hits/total/relation").asText()).isEqualTo("eq");
        assertThat(item.at("/hits/hits/0/_index").asText()).isEqualTo(CASES_INDEX);
        assertThat(item.at("/hits/hits/0/_id").asText()).isNotBlank();
    }

    @Test
    void singleSearchResponsesCarryTheSameEnvelopeWithoutAStatus() throws Exception {
        var response = mapper.readTree(
            post("/" + CASES_INDEX + "/_search", caseListBody("{\"match_all\":{}}", "[]")).body());

        assertThat(response.get("took").isNumber()).isTrue();
        assertThat(response.get("timed_out").isBoolean()).isTrue();
        assertThat(response.has("_shards")).isTrue();
        assertThat(response.at("/hits/total/value").asInt()).isEqualTo(3);
        assertThat(response.has("status")).isFalse();
        assertThat(response.has("responses")).isFalse();
    }

    @Test
    void anIndexForACaseTypeWithNoCasesReturnsNoHitsRatherThanAnError() throws Exception {
        var item = firstItem(msearch("other_cases", caseListBody("{\"match_all\":{}}", "[]")));

        assertThat(item.at("/status").asInt()).isEqualTo(200);
        assertThat(item.at("/hits/total/value").asInt()).isZero();
    }

    @Test
    void anIndexThatIsNotACaseIndexIsRejected() throws Exception {
        var items = mapper.readTree(msearch("logstash-2024", caseListBody("{\"match_all\":{}}", "[]")).body())
            .at("/responses");

        assertThat(items.get(0).at("/status").asInt()).isEqualTo(400);
        assertThat(items.get(0).at("/error/reason").asText()).contains("is not an index the stub serves");
    }

    /**
     * The literal wire body, captured by round tripping CCD's case list request through
     * JestToESConverter and co.elastic.clients' own serialisation. This, rather than the json CCD
     * assembles, is what reaches the stub.
     */
    @Test
    void caseListExactlyAsTheElasticsearchClientPutsItOnTheWire() throws Exception {
        var body = """
            {"from":0,"query":{"bool":{"boost":1.0,"filter":[{"terms":{"security_classification":["PUBLIC"],\
            "boost":1.0}},{"terms":{"state":["draft"],"boost":1.0}}],"must":[{"wrapper":\
            {"query":"eyJib29sIjp7Im11c3QiOltdfX0="}},{"bool":{"boost":1.0,"should":[{"bool":{"boost":1.0,"must":\
            [{"term":{"jurisdiction.keyword":{"value":"DIVORCE"}}},{"terms":{"state.keyword":["Draft"],\
            "boost":1.0}},{"terms":{"reference.keyword":[1234567890123456],"boost":1.0}},{"terms":\
            {"security_classification":["PUBLIC"],"boost":1.0}},{"bool":{"boost":1.0,"should":\
            [{"match_phrase_prefix":{"data.CaseAccessCategory":{"query":"Civil/Standard"}}}]}}]}}]}}]}},\
            "size":25,"sort":[{"data.applicant1LastName.keyword":{"order":"asc"}},{"created_date":{}}],\
            "_source":{"includes":["jurisdiction","case_type_id","state","reference","created_date",\
            "last_modified","last_state_modified_date","security_classification","data_classification","data",\
            "supplementary_data.*"]}}""";

        var item = firstItem(msearch(CASES_INDEX, body));

        assertThat(item.at("/status").asInt()).isEqualTo(200);
        assertThat(references(item)).containsExactly("1234567890123456");
        assertThat(item.at("/hits/hits/0/_source/supplementary_data/HMCTSServiceId").asText()).isEqualTo("AAA1");
    }

    /**
     * The same for a global search party query.
     */
    @Test
    void globalSearchExactlyAsTheElasticsearchClientPutsItOnTheWire() throws Exception {
        var body = """
            {"from":0,"query":{"bool":{"boost":1.0,"minimum_should_match":"1","must":[{"terms":\
            {"case_type_id":["nfd"],"boost":1.0}},{"bool":{"boost":1.0,"minimum_should_match":"1","should":\
            [{"nested":{"boost":1.0,"ignore_unmapped":false,"path":"data.SearchCriteria.SearchParties.value",\
            "query":{"bool":{"boost":1.0,"must":[{"wildcard":\
            {"data.SearchCriteria.SearchParties.value.Name.keyword":{"boost":1.0,"wildcard":"*Smith*"}}},\
            {"match_phrase":{"data.SearchCriteria.SearchParties.value.PostCode":{"analyzer":"standard",\
            "query":"SW1A 1AA"}}},{"range":{"data.SearchCriteria.SearchParties.value.DateOfBirth":{"boost":1.0,\
            "gte":"1990-01-01","lte":"1990-01-01"}}}]}},"score_mode":"sum"}}]}}],"should":[{"wildcard":\
            {"reference.keyword":{"boost":1.0,"wildcard":"12*"}}}]}},"size":500,\
            "sort":[{"data.caseNameHmctsInternal.keyword":{"order":"desc"}},{"created_date":{"order":"asc"}}],\
            "_source":{"includes":["reference","data.CaseAccessCategory",\
            "data.caseManagementCategory.value.code","supplementary_data.HMCTSServiceId"]}}""";

        var item = firstItem(msearch(EsStubSources.GLOBAL_SEARCH_INDEX, body));

        assertThat(item.at("/status").asInt()).isEqualTo(200);
        assertThat(references(item)).containsExactly("1234567890123456");
    }

    /**
     * The exact json org.elasticsearch's query builders emit for a security filtered case search,
     * captured from ElasticsearchCaseSearchRequestSecurity's clause vocabulary. Every leaf carries
     * a default boost alongside the field name, which the stub has to tolerate.
     */
    @Test
    void securityWrappedCaseSearchExactlyAsTheDataStoreSerialisesIt() throws Exception {
        var query = """
            {"bool":{"must":[{"wrapper":{"query":"eyJib29sIjp7Im11c3QiOltdfX0="}},{"bool":{"should":[{"bool":{"must":\
            [{"term":{"jurisdiction.keyword":{"value":"DIVORCE"}}},{"terms":{"state.keyword":["Draft"],"boost":1.0}},\
            {"terms":{"reference.keyword":[1234567890123456],"boost":1.0}},{"terms":{"security_classification":\
            ["PUBLIC"],"boost":1.0}},{"bool":{"should":[{"match_phrase_prefix":{"data.CaseAccessCategory":\
            {"query":"Civil/Standard"}}}],"boost":1.0}}],"boost":1.0}}],"boost":1.0}}],"filter":[{"terms":\
            {"security_classification":["PUBLIC"],"boost":1.0}},{"terms":{"state":["draft"],"boost":1.0}},\
            {"terms":{"reference":[1234567890123456],"boost":1.0}}],"boost":1.0}}""";

        var item = firstItem(msearch(CASES_INDEX, caseListBody(query, "[]")));

        assertThat(item.at("/status").asInt()).isEqualTo(200);
        assertThat(references(item)).containsExactly("1234567890123456");
    }

    /**
     * The exact json GlobalSearchQueryBuilder emits for a party search, with only the leaf values
     * changed to match the cases inserted here.
     */
    @Test
    void globalSearchExactlyAsTheDataStoreSerialisesIt() throws Exception {
        var query = """
            {"bool":{"must":[{"terms":{"case_type_id":["nfd"],"boost":1.0}},{"terms":{"jurisdiction":["divorce"],\
            "boost":1.0}},{"bool":{"should":[{"nested":{"query":{"bool":{"must":[{"wildcard":\
            {"data.SearchCriteria.SearchParties.value.Name.keyword":{"wildcard":"*Smith*","boost":1.0}}},\
            {"match_phrase":{"data.SearchCriteria.SearchParties.value.PostCode":{"query":"SW1A 1AA",\
            "analyzer":"standard"}}},{"range":{"data.SearchCriteria.SearchParties.value.DateOfBirth":\
            {"gte":"1990-01-01","lte":"1990-01-01","boost":1.0}}}],"boost":1.0}},\
            "path":"data.SearchCriteria.SearchParties.value","ignore_unmapped":false,"score_mode":"sum",\
            "boost":1.0}}],"minimum_should_match":"1","boost":1.0}}],"should":[{"wildcard":{"reference.keyword":\
            {"wildcard":"12*","boost":1.0}}}],"minimum_should_match":"1","boost":1.0}}""";

        var item = firstItem(msearch(EsStubSources.GLOBAL_SEARCH_INDEX, globalSearchBody(query)));

        assertThat(item.at("/status").asInt()).isEqualTo(200);
        assertThat(references(item)).containsExactly("1234567890123456");
    }

    /**
     * The clause vocabulary a service team's own search builds, from nfdiv's CcdSearchService.
     */
    @Test
    void serviceTeamQueryExactlyAsTheSdkSerialisesIt() throws Exception {
        var body = """
            {"from":0,"size":100,"query":{"bool":{"must":[{"match":{"state":{"query":"draft"}}},\
            {"exists":{"field":"data.applicant1LastName","boost":1.0}}],"filter":[{"range":\
            {"data.applicant1LastName":{"lte":"Z","boost":1.0}}}],"must_not":[{"match":\
            {"data.applicant1FirstName":{"query":"carol"}}}],"boost":1.0}}}""";

        var item = firstItem(msearch(CASES_INDEX, body));

        assertThat(item.at("/status").asInt()).isEqualTo(200);
        assertThat(references(item)).containsExactly("1234567890123456");
    }

    /**
     * The request body the data store builds for a work basket case list: the caller's query,
     * from and size, the sorts the sort service applied, and the merged _source.
     */
    private String caseListBody(String query, String sort) {
        return "{\"from\":0,\"size\":25,\"query\":" + query + ",\"sort\":" + sort + ",\"_source\":{\"includes\":"
            + "[\"jurisdiction\",\"case_type_id\",\"state\",\"reference\",\"created_date\",\"last_modified\","
            + "\"last_state_modified_date\",\"security_classification\",\"data_classification\",\"data\"]}}";
    }

    /**
     * The request body GlobalSearchServiceImpl builds, whose _source names individual data paths.
     */
    private String globalSearchBody(String query) {
        return "{\"from\":0,\"size\":500,\"query\":" + query + ",\"_source\":{\"includes\":"
            + "[\"jurisdiction\",\"case_type_id\",\"state\",\"reference\",\"created_date\",\"last_modified\","
            + "\"last_state_modified_date\",\"security_classification\",\"data_classification\","
            + "\"data.CaseAccessCategory\",\"data.caseManagementCategory.value.code\","
            + "\"data.caseManagementCategory.value.label\",\"data.caseManagementLocation\","
            + "\"data.caseNameHmctsInternal\",\"data.SearchCriteria.OtherCaseReferences\","
            + "\"supplementary_data.HMCTSServiceId\"]}}";
    }

    private List<String> references(JsonNode item) {
        var references = new java.util.ArrayList<String>();
        item.at("/hits/hits").forEach(hit -> references.add(hit.at("/_source/reference").asText()));
        return references;
    }

    private JsonNode firstItem(HttpResponse<String> response) throws Exception {
        return mapper.readTree(response.body()).at("/responses").get(0);
    }

    private String header(String index) {
        return "{\"index\":[\"" + index + "\"]}";
    }

    private HttpResponse<String> msearch(String index, String body) throws Exception {
        var ndjson = body.startsWith("{\"index\"") ? body : header(index) + "\n" + body + "\n";
        return post("/_msearch", ndjson);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(baseUrl + path))
            .header("Content-Type", "application/x-ndjson")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(), HttpResponse.BodyHandlers.ofString());
    }
}
