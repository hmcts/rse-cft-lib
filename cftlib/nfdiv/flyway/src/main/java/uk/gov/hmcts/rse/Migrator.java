package uk.gov.hmcts.rse;

import org.flywaydb.core.Flyway;
import org.postgresql.jdbc.PgConnection;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

// TODO: redo all this properly in the ccd sdk gradle plugin
public class Migrator {
    public static void init(Connection connection) throws SQLException {
        migrateLib(connection);
        String path;
        if (new File("nfdiv").exists()) {
            path = "filesystem:nfdiv/src/main/resources/db/migration";
        } else {
            path = "filesystem:src/main/resources/db/migration";
        }

        PgConnection c = (PgConnection) connection;

        Flyway.configure().dataSource(c.getURL(), "test", "test")
                .detectEncoding(true)
                .locations(path)
                          .load()
                          .migrate();
    }

    private static void migrateLib(Connection connection) throws SQLException {
        String path;
        if (new File("nfdiv").exists()) {
            path = "filesystem:dtsse-ccd-config-generator/data-runtime/src/main/resources/dataruntime-db/migration";
        } else {
            path = "filesystem:../dtsse-ccd-config-generator/data-runtime/src/main/resources/dataruntime-db/migration";
        }

        PgConnection c = (PgConnection) connection;

        Flyway.configure().dataSource(c.getURL(), "test", "test")
            .detectEncoding(true)
            .schemas("ccd")
            .locations(path)
            .load()
            .migrate();
    }
}
