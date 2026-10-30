package uk.gov.hmcts.rse.ccd.lib;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * The stub starts serving while a service's migrations are still running, so what it concludes
 * about a database has to be provisional.
 *
 * <p>The decentralised runtime creates {@code ccd.case_data} many migrations before it adds
 * {@code case_event.case_revision}, which is what tells the two shapes apart. A search arriving in
 * between sees a database that looks centralised, and a conclusion kept for the run would answer
 * every later search from the wrong shape.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EsStubSourceShapeTest {

    private static EmbeddedPostgres postgres;

    @BeforeAll
    void startPostgres() throws Exception {
        postgres = EmbeddedPostgres.builder().start();
        try (var c = connect("postgres"); var s = c.createStatement()) {
            s.execute("create database pcs");
        }
    }

    @AfterAll
    void stopPostgres() throws Exception {
        if (postgres != null) {
            postgres.close();
        }
    }

    @Test
    void followsADatabaseFromEmptyThroughMigrationToDecentralised() throws Exception {
        var sources = new EsStubSources("pcs", EsStubSourceShapeTest::connect,
            java.util.Set.of("pcs"));

        // Before the service's migrations: no case tables, so not a source at all.
        assertThat(sources.sourcesFor("pcs_cases")).isEmpty();

        // Part way through: case_data exists, case_revision does not, so it reads as centralised.
        try (var c = connect("pcs"); var s = c.createStatement()) {
            s.execute("create schema ccd");
            s.execute("create table ccd.case_data (id bigint, reference bigint, case_type_id varchar(255))");
            s.execute("create table ccd.case_event (id bigint, case_data_id bigint)");
            s.execute("insert into ccd.case_data values (1, 1234567890123456, 'PCS')");
        }
        Awaitility.await("the earlier answer stops being used")
            .atMost(java.time.Duration.ofSeconds(10))
            .pollInterval(java.time.Duration.ofMillis(250))
            .until(() -> !sources.sourcesFor("pcs_cases").isEmpty());
        assertThat(sources.sourcesFor("pcs_cases").get(0).decentralised())
            .as("case_revision has not been added yet")
            .isFalse();

        // Once the migration that adds it has run, the same database reads as decentralised.
        try (var c = connect("pcs"); var s = c.createStatement()) {
            s.execute("alter table ccd.case_event add column case_revision bigint");
        }
        Awaitility.await("the shape is re-read rather than remembered")
            .atMost(java.time.Duration.ofSeconds(10))
            .pollInterval(java.time.Duration.ofMillis(250))
            .until(() -> sources.sourcesFor("pcs_cases").get(0).decentralised());
    }

    @Test
    void tellsTwoSchemasOfOneDatabaseApart() throws Exception {
        try (var c = connect("postgres"); var s = c.createStatement()) {
            s.execute("create database twoschemas");
        }
        try (var c = connect("twoschemas"); var s = c.createStatement()) {
            // Both schemas hold case data, which is why a source names one of them, and only
            // one of them holds this case type.
            s.execute("create schema ccd");
            s.execute("create table ccd.case_data (id bigint, reference bigint, case_type_id varchar(255))");
            s.execute("create table ccd.case_event (id bigint, case_data_id bigint)");
            s.execute("create table public.case_data (id bigint, reference bigint, case_type_id varchar(255))");
            s.execute("create table public.case_event (id bigint, case_data_id bigint)");
            s.execute("insert into public.case_data values (1, 1234567890123456, 'PCS')");
        }

        var sources = new EsStubSources("twoschemas:ccd,twoschemas:public",
            EsStubSourceShapeTest::connect, java.util.Set.of());

        // Answering from ccd would search a schema this case type has no rows in, so the search
        // would come back empty — or fail, where that schema has no such table at all. Asked
        // twice, because the first answer is what fills the cache that the second reads: a
        // remembered hit has to belong to the schema it was found in.
        assertThat(sources.sourcesFor("pcs_cases"))
            .extracting(EsStubSources.Source::schema)
            .containsExactly("public");
        assertThat(sources.sourcesFor("pcs_cases"))
            .as("the schema the case type was found in is the one remembered")
            .extracting(EsStubSources.Source::schema)
            .containsExactly("public");
    }

    private static Connection connect(String database) throws SQLException {
        return DriverManager.getConnection(
            postgres.getJdbcUrl("postgres", database) + "&stringtype=unspecified", "postgres", "postgres");
    }
}
