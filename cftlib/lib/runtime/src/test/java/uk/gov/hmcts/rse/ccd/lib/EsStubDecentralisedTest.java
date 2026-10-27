package uk.gov.hmcts.rse.ccd.lib;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * The same searches against a decentralised service's own database, where the document is built
 * from ccd.case_data joined to the latest revision in ccd.case_event.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EsStubDecentralisedTest {

    private static EmbeddedPostgres postgres;
    private static EsStub stub;
    private static String baseUrl;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeAll
    void startStack() throws Exception {
        postgres = EmbeddedPostgres.builder().start();
        try (var c = connect("postgres"); var s = c.createStatement()) {
            s.execute("create database pcs");
        }
        createSchema();
        insertCases();

        stub = new EsStub(new EsStubSources("pcs", EsStubDecentralisedTest::connect));
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
     * The shape the SDK's decentralised runtime migrations leave behind.
     */
    private void createSchema() throws Exception {
        try (var c = connect("pcs"); var s = c.createStatement()) {
            s.execute("create schema ccd");
            s.execute("create type ccd.securityclassification as enum ('PUBLIC', 'PRIVATE', 'RESTRICTED')");
            s.execute("""
                create table ccd.case_data (
                    reference bigint primary key,
                    id bigint not null,
                    version integer not null default 1,
                    case_revision bigint not null default 1,
                    created_date timestamp without time zone default now() not null,
                    security_classification ccd.securityclassification not null,
                    last_state_modified_date timestamp without time zone,
                    last_modified timestamp without time zone,
                    jurisdiction varchar(255) not null,
                    case_type_id varchar(255) not null,
                    state varchar(255) not null,
                    data jsonb not null,
                    supplementary_data jsonb not null default '{}'
                )
                """);
            s.execute("create unique index idx_case_data_id on ccd.case_data (id)");
            s.execute("""
                create table ccd.case_event (
                    id bigserial primary key,
                    created_date timestamp without time zone default now() not null,
                    case_data_id bigint not null references ccd.case_data(id),
                    case_revision bigint not null,
                    version integer not null default 1,
                    event_id varchar(70) not null,
                    data jsonb not null
                )
                """);
        }
    }

    private void insertCases() throws Exception {
        try (var c = connect("pcs"); var s = c.createStatement()) {
            s.execute("""
                insert into ccd.case_data(reference, id, case_revision, created_date, security_classification,
                    last_modified, jurisdiction, case_type_id, state, data, supplementary_data)
                values
                  (1716234206443000, 1, 2, '2024-03-01 09:00:00', 'PUBLIC', '2024-03-02 09:00:00',
                   'PCS', 'PCS', 'AwaitingSubmissionToHmcts', '{}'::jsonb, '{"HMCTSServiceId": "AAA6"}'::jsonb),
                  (1716234206443001, 2, 1, '2024-03-03 09:00:00', 'PRIVATE', null,
                   'PCS', 'PCS', 'Submitted', '{}'::jsonb, '{}'::jsonb)
                """);
            // Two revisions of the first case: only the latest should be visible.
            s.execute("""
                insert into ccd.case_event(case_data_id, case_revision, event_id, data)
                values
                  (1, 1, 'create', '{"claimantName": "Stale Name"}'::jsonb),
                  (1, 2, 'update',
                   '{"claimantName": "Ann-Marie Smith", "SearchCriteria": {}, "caseNameHmctsInternal": "Smith v X"}'
                   ::jsonb),
                  (2, 1, 'create', '{"claimantName": "Bob Jones"}'::jsonb)
                """);
        }
    }

    @Test
    void readsTheLatestRevisionOfEachCase() throws Exception {
        var item = firstItem(msearch("pcs_cases", body("{\"match_all\":{}}")));

        assertThat(item.at("/hits/total/value").asInt()).isEqualTo(2);
        assertThat(names(item)).containsExactlyInAnyOrder("Ann-Marie Smith", "Bob Jones");
    }

    @Test
    void theDocumentHasTheDecentralisedShape() throws Exception {
        var search = "{\"query\":{\"term\":{\"reference.keyword\":\"1716234206443000\"}}}";
        var source = firstItem(msearch("pcs_cases", search)).at("/hits/hits/0/_source");

        assertThat(source.fieldNames()).toIterable().containsExactlyInAnyOrder(
            "reference", "case_revision", "case_type_id", "index_id", "created_date", "jurisdiction", "id",
            "state", "security_classification", "last_state_modified_date", "supplementary_data", "event_id",
            "data", "last_modified");
        // The decentralised indexer writes no data classification.
        assertThat(source.has("data_classification")).isFalse();
        assertThat(source.at("/case_revision").asInt()).isEqualTo(2);
        assertThat(source.at("/index_id").asText()).isEqualTo("pcs_cases");
    }

    @Test
    void lastModifiedFallsBackToCreatedDate() throws Exception {
        var search = "{\"query\":{\"term\":{\"reference.keyword\":\"1716234206443001\"}}}";
        var source = firstItem(msearch("pcs_cases", search)).at("/hits/hits/0/_source");

        assertThat(source.at("/last_modified").asText()).startsWith("2024-03-03");
    }

    @Test
    void matchOnEventData() throws Exception {
        var query = "{\"bool\":{\"must\":[{\"match\":{\"data.claimantName\":"
            + "{\"operator\":\"and\",\"query\":\"smith\"}}}]}}";
        var item = firstItem(msearch("pcs_cases", body(query)));

        assertThat(names(item)).containsExactly("Ann-Marie Smith");
    }

    @Test
    void globalSearchUsesTheSameFilterAsTheIndexer() throws Exception {
        var item = firstItem(msearch(EsStubSources.GLOBAL_SEARCH_INDEX, body("{\"match_all\":{}}")));
        var source = item.at("/hits/hits/0/_source");

        assertThat(item.at("/hits/total/value").asInt()).isEqualTo(1);
        assertThat(source.at("/index_id").asText()).isEqualTo("global_search");
        assertThat(source.at("/data/caseNameHmctsInternal").asText()).isEqualTo("Smith v X");
        assertThat(source.at("/data").has("claimantName")).isFalse();
        assertThat(source.at("/supplementary_data/HMCTSServiceId").asText()).isEqualTo("AAA6");
        assertThat(source.has("created_date")).isFalse();
        assertThat(source.has("last_modified")).isFalse();
    }

    private String body(String query) {
        return "{\"from\":0,\"size\":25,\"query\":" + query + "}";
    }

    private List<String> names(JsonNode item) {
        var names = new ArrayList<String>();
        item.at("/hits/hits").forEach(hit -> names.add(hit.at("/_source/data/claimantName").asText()));
        return names;
    }

    private JsonNode firstItem(HttpResponse<String> response) throws Exception {
        return mapper.readTree(response.body()).at("/responses").get(0);
    }

    private HttpResponse<String> msearch(String index, String body) throws Exception {
        var ndjson = "{\"index\":[\"" + index + "\"]}\n" + body + "\n";
        return client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/_msearch"))
            .header("Content-Type", "application/x-ndjson")
            .POST(HttpRequest.BodyPublishers.ofString(ndjson))
            .build(), HttpResponse.BodyHandlers.ofString());
    }
}
