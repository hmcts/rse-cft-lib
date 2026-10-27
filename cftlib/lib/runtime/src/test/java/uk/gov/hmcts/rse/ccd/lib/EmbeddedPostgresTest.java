package uk.gov.hmcts.rse.ccd.lib;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EmbeddedPostgresTest {

    @Test
    void servesSqlAndKeepsItsDataAcrossARestart(@TempDir Path dir) throws Exception {
        var port = freePort();
        var data = dir.resolve("postgres");
        var log = dir.resolve("logs/postgres.log");

        var first = new EmbeddedPostgres(data, log, port, false, false);
        first.start();
        try {
            assertThat(Files.getPosixFilePermissions(PostgresBinaries.extractionDirectory()))
                .doesNotContain(PosixFilePermission.OTHERS_WRITE);

            try (var c = connect(port, "postgres")) {
                exec(c, "create database datastore");
            }
            try (var c = connect(port, "datastore")) {
                // The extensions the CFT services and CFTLibApiImpl create for themselves.
                exec(c, "create extension if not exists btree_gin");
                exec(c, "create extension if not exists pgcrypto");
                exec(c, "create table case_data (id serial primary key, data jsonb not null)");
                exec(c, "create index case_data_data on case_data using gin (data)");
                exec(c, "insert into case_data (data) values ('{\"reference\": 1234}'::jsonb)");
                assertThat(queryString(c, "select data->>'reference' from case_data")).isEqualTo("1234");
            }
        } finally {
            first.stop();
        }

        assertThat(Ports.isListening(port)).isFalse();
        assertThat(log).exists();

        var second = new EmbeddedPostgres(data, log, port, false, false);
        second.start();
        try (var c = connect(port, "datastore")) {
            assertThat(queryString(c, "select data->>'reference' from case_data")).isEqualTo("1234");
        } finally {
            second.stop();
        }
    }

    @Test
    void adoptsAPostgresThatIsAlreadyListening(@TempDir Path dir) throws Exception {
        var port = freePort();
        var log = dir.resolve("logs/postgres.log");

        var running = new EmbeddedPostgres(dir.resolve("running"), log, port, false, false);
        running.start();
        try {
            var unused = dir.resolve("unused");
            var adopter = new EmbeddedPostgres(unused, log, port, false, false);
            adopter.start();

            assertThat(unused).doesNotExist();

            // Stopping an adopted instance must leave the database alone.
            adopter.stop();
            try (var c = connect(port, "postgres")) {
                assertThat(queryString(c, "select 'up'")).isEqualTo("up");
            }
        } finally {
            running.stop();
        }
    }

    @Test
    void cleanBootStopsThePreviousRunAndRecreatesTheData(@TempDir Path dir) throws Exception {
        var port = freePort();
        var data = dir.resolve("postgres");
        var log = dir.resolve("logs/postgres.log");

        var previous = new EmbeddedPostgres(data, log, port, false, true);
        previous.start();
        try (var c = connect(port, "postgres")) {
            exec(c, "create database datastore");
        }
        var pid = postmasterPid(data);
        previous.stop();

        assertThat(pid).isPresent();
        assertThat(Ports.isListening(port)).as("postgres is left running for the next boot").isTrue();

        try {
            var clean = new EmbeddedPostgres(data, log, port, true, false);
            clean.start();
            try (var c = connect(port, "postgres")) {
                assertThat(queryString(c, "select count(*)::text from pg_database where datname = 'datastore'"))
                    .isEqualTo("0");
            } finally {
                clean.stop();
            }
        } finally {
            pid.flatMap(ProcessHandle::of).ifPresent(ProcessHandle::destroyForcibly);
        }
    }

    @Test
    void derivesTheDataDirectoryFromTheLogFolder() {
        assertThat(EmbeddedPostgres.buildDirectory("/work/pcs-api/build/cftlib/logs"))
            .isEqualTo(Path.of("/work/pcs-api/build/cftlib"));
        assertThat(EmbeddedPostgres.logFolder("/work/pcs-api/build/cftlib/logs"))
            .isEqualTo(Path.of("/work/pcs-api/build/cftlib/logs"));

        assertThat(EmbeddedPostgres.buildDirectory(null))
            .isEqualTo(Path.of("build", "cftlib").toAbsolutePath().normalize());
        assertThat(EmbeddedPostgres.logFolder(" "))
            .isEqualTo(Path.of("build", "cftlib", "logs").toAbsolutePath().normalize());
    }

    private static Optional<Long> postmasterPid(Path dataDirectory) throws IOException {
        var lines = Files.readAllLines(dataDirectory.resolve("postmaster.pid"));
        return lines.isEmpty() ? Optional.empty() : Optional.of(Long.parseLong(lines.get(0).trim()));
    }

    private static Connection connect(int port, String database) throws SQLException {
        return DriverManager.getConnection(
            "jdbc:postgresql://localhost:" + port + "/" + database, "postgres", "postgres");
    }

    private static void exec(Connection c, String sql) throws SQLException {
        try (var s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private static String queryString(Connection c, String sql) throws SQLException {
        try (var s = c.createStatement(); var rs = s.executeQuery(sql)) {
            assertThat(rs.next()).isTrue();
            return rs.getString(1);
        }
    }

    private static int freePort() throws IOException {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
