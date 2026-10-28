package uk.gov.hmcts.rse.ccd.lib;

import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.SneakyThrows;

/**
 * Readiness checks for the dependencies cftlib does not run in-process,
 * shared by every {@link DependencyProvider}.
 */
public class Readiness {

    @SneakyThrows
    boolean authReady() {
        if (CftlibConfig.localAuth()) {
            try {
                // Wait for Idam Simulator to come up.
                var c = (HttpURLConnection) new URL("http://localhost:5062/health")
                    .openConnection();
                if (c.getResponseCode() != 200) {
                    throw new RuntimeException();
                }
            } catch (Exception e) {
                System.out.println("Idam not ready...");
                return false;
            }
        }
        return true;
    }

    @SneakyThrows
    boolean dbReady() {
        try (var c = DriverManager.getConnection(
            "jdbc:postgresql://" + CftlibConfig.dbHost() + ":" + CftlibConfig.dbPort() + "/postgres",
            CftlibConfig.dbUsername(), CftlibConfig.dbPassword())) {

            var dbs = List.of(Project.Datastore, Project.Definitionstore, Project.Userprofile, Project.AM)
                .stream().map(Objects::toString)
                .collect(Collectors.toCollection(ArrayList::new));

            dbs.add("cft_task_db");
            if (composeProfiles(System.getenv()).contains("camunda")) {
                dbs.add("camunda");
            }
            if (WorkAllocation.enabled(System.getenv())) {
                dbs.add("wa_case_event_messages_db");
                dbs.add("wa_workflow_api");
            }
            if (Hearings.enabled(System.getenv())) {
                dbs.add("hmc_cft_hearing_service");
            }

            var additionalDbs = System.getenv("RSE_LIB_ADDITIONAL_DATABASES");
            if (additionalDbs != null) {
                dbs.addAll(List.of(additionalDbs.split(",")));
            }

            // Create the databases if necessary.
            for (var db : dbs) {
                var s = c.prepareStatement(
                    String.format("SELECT datname FROM pg_catalog.pg_database WHERE lower(datname) = lower('%s')", db));
                s.execute();
                if (!s.getResultSet().next()) {
                    c.prepareStatement("create database " + db).execute();
                }
            }
        } catch (SQLException s) {
            System.out.println("DB not yet available...");
            throw s;
        }
        return true;
    }

    static List<String> composeProfiles(Map<String, String> env) {
        var profiles = env.getOrDefault("COMPOSE_PROFILES", "");
        return Arrays.stream(profiles.split(",")).map(String::trim).filter(p -> !p.isEmpty()).toList();
    }

    boolean serviceBusReady() {
        try {
            var c = (HttpURLConnection) new URL("http://localhost:"
                + ControlPlane.getEnvVar("SERVICEBUS_HTTP_PORT", 5300) + "/health").openConnection();
            if (c.getResponseCode() == 200) {
                return true;
            }
        } catch (Exception e) {
            // Not listening yet.
        }
        System.out.println("Service Bus emulator not yet available...");
        return false;
    }

    @SneakyThrows
    boolean esReady() {
        try {
            var c = (HttpURLConnection) new URL(CftlibConfig.esUrl() + "/_cat/health")
                .openConnection();
            if (c.getResponseCode() != 200) {
                return false;
            }
        } catch (Exception e) {
            System.out.println("ES not yet available...");
            throw e;
        }
        return true;
    }
}
