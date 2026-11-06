package uk.gov.hmcts.rse.ccd.lib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkAllocationDeliveryTest {
    @TempDir
    Path directory;
    private EmbeddedPostgres database;
    private HttpServer receiver;
    private String databaseUrl;
    private WorkAllocation relay;
    private final List<String> received = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        databaseUrl = "jdbc:postgresql://localhost:" + port + "/";
        database = new EmbeddedPostgres(directory.resolve("postgres"), directory.resolve("postgres.log"),
            port, false, false);
        database.start();
        try (var c = DriverManager.getConnection(databaseUrl + "postgres", "postgres", "postgres")) {
            c.createStatement().execute("create database datastore");
        }
        try (var c = connect()) {
            c.createStatement().execute("create table message_queue_candidates (id bigserial primary key,"
                + " message_type text, message_information jsonb, time_stamp timestamp default localtimestamp,"
                + " published timestamp)");
        }
        // The downstream HTTP recipient is deliberately small; delivery and transactions use the real poller and DB.
        receiver = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        receiver.createContext("/messages", exchange -> {
            var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            received.add(body);
            exchange.sendResponseHeaders(body.contains("poison") ? 500 : 200, -1);
            exchange.close();
        });
        receiver.start();
        relay = new WorkAllocation(new CFTLibApiImpl(), List.of(new WorkAllocation.Source("datastore", "public")),
            databaseUrl, URI.create("http://localhost:" + receiver.getAddress().getPort() + "/messages"));
    }

    @AfterEach
    void stop() {
        if (receiver != null) {
            receiver.stop(0);
        }
        if (database != null) {
            database.stop();
        }
    }

    @Test
    void deliversAnEarlierIdThatCommitsAfterALaterIdWithoutRepeatingEither() throws Exception {
        relay.startDelivery();
        try (var earlier = connect(); var later = connect()) {
            earlier.setAutoCommit(false);
            insert(earlier, "first");
            insert(later, "second");
            relay.deliverCaseEvents();
            assertThat(received).hasSize(1);
            earlier.commit();
            relay.deliverCaseEvents();
            relay.deliverCaseEvents();
            assertThat(received).hasSize(2);
            assertThat(received.get(0)).contains("second");
            assertThat(received.get(1)).contains("first");
            assertThat(publishedCount()).isEqualTo(2);
        }
    }

    @Test
    void ignoresPreviousRunsButDeliversNewEvents() throws Exception {
        try (var c = connect()) {
            insert(c, "previous-run");
        }
        relay.startDelivery();
        try (var c = connect()) {
            insert(c, "this-run");
        }
        relay.deliverCaseEvents();
        relay.deliverCaseEvents();
        assertThat(received).singleElement().asString().contains("this-run");
        assertThat(publishedCount()).isEqualTo(1);
    }

    @Test
    void retriesInOrderThenSetsAsidePoisonEventsWithoutRetryingThemForever() throws Exception {
        relay.startDelivery();
        try (var c = connect()) {
            insert(c, "poison");
            insert(c, "next-event");
        }
        for (int attempt = 0; attempt < 4; attempt++) {
            assertThatThrownBy(relay::deliverCaseEvents).isInstanceOf(IllegalStateException.class);
        }
        assertThat(received).hasSize(4).allMatch(body -> body.contains("poison"));
        relay.deliverCaseEvents();
        relay.deliverCaseEvents();
        assertThat(received).hasSize(6);
        assertThat(received.get(5)).contains("next-event");
        assertThat(publishedCount()).isEqualTo(1);
    }

    private Connection connect() throws Exception {
        return DriverManager.getConnection(databaseUrl + "datastore", "postgres", "postgres");
    }

    private void insert(Connection connection, String event) throws Exception {
        try (var statement = connection.prepareStatement("insert into message_queue_candidates"
            + " (message_type, message_information) values ('CASE_EVENT', ?::jsonb)")) {
            statement.setString(1, "{\"event\":\"" + event + "\"}");
            statement.executeUpdate();
        }
    }

    private int publishedCount() throws Exception {
        try (var c = connect();
             var result = c.createStatement().executeQuery(
                 "select count(*) from message_queue_candidates where published is not null")) {
            result.next();
            return result.getInt(1);
        }
    }
}
