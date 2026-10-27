package uk.gov.hmcts.rse.ccd.lib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;

class EsStubSourcesTest {

    private static final EsStubSources.Connections NO_CONNECTIONS = database -> {
        throw new SQLException("not expected");
    };

    @Test
    void parsesAListOfDatabasesAndSchemas() {
        var sources = new EsStubSources("datastore, pcs:ccd ,", NO_CONNECTIONS);

        assertThat(sources.configured()).containsExactly("datastore", "pcs:ccd");
    }

    @Test
    void rejectsNamesThatCouldNotBeIdentifiers() {
        var sources = new EsStubSources("data\"store", NO_CONNECTIONS);

        assertThatThrownBy(() -> sources.sourcesFor("nfd_cases"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("RSE_LIB_ES_STUB_SOURCES");
    }

    @Test
    void rejectsAnEmptyConfiguration() {
        assertThatThrownBy(() -> new EsStubSources(" , ", NO_CONNECTIONS))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readsTheCaseTypeFromACaseIndexName() {
        assertThat(EsStubSources.caseTypeOf("care_supervision_epo_cases")).isEqualTo("care_supervision_epo");
    }

    @Test
    void rejectsIndexNamesThatAreNotCcdIndices() {
        assertThatThrownBy(() -> EsStubSources.caseTypeOf("logstash-2024"))
            .isInstanceOf(EsStubException.class)
            .hasMessageContaining("is not an index the stub serves");
        assertThatThrownBy(() -> EsStubSources.caseTypeOf("_cases"))
            .isInstanceOf(EsStubException.class);
    }

    @Test
    void theDocumentViewNamesTheConfiguredSchema() {
        var sources = new EsStubSources("datastore", NO_CONNECTIONS);
        var centralised = new EsStubSources.Source("datastore", "public", false);
        var decentralised = new EsStubSources.Source("pcs", "ccd", true);

        assertThat(sources.documentView(centralised, "nfd_cases"))
            .contains("from \"public\".\"case_data\"")
            .contains("r.data_classification as data_classification")
            .contains("row_to_json(r)::jsonb as document");
        assertThat(sources.documentView(decentralised, "pcs_cases"))
            .contains("from \"ccd\".\"case_data\" cd")
            .contains("from \"ccd\".\"case_event\" ce")
            .contains("null::jsonb as data_classification");
        assertThat(sources.documentView(centralised, EsStubSources.GLOBAL_SEARCH_INDEX))
            .contains("jsonb_exists(base.data, 'SearchCriteria')")
            .contains("null::timestamp as created_date");
    }
}
