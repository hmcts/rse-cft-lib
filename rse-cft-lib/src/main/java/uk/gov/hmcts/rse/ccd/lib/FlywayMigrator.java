package uk.gov.hmcts.rse.ccd.lib;

import java.sql.Connection;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class FlywayMigrator implements FlywayMigrationStrategy {

  @Autowired
  DataSource dataSource;

  // Run common component SQL migrations placing each DB in its own schema.
  // We do this by running the migrations in the public schema and then renaming it.
  // Migrating existing DBs requires we rename back to the public schema.
  // This is necessary since many migrations are written to explicitly reference the
  // default postgres public schema.
  @SneakyThrows
  @PostConstruct
  public void migrate() {
    for (String module : List.of(
        "datastore",
        "definitionstore",
        "userprofile",
        "am"
    )) {
      try (Connection c = dataSource.getConnection()) {
        c.createStatement().execute("drop schema if exists public cascade");
        if (c.createStatement().executeQuery(
            "select schema_name from information_schema.schemata where schema_name = '" + module + "'").next()) {
          c.createStatement().execute("alter schema " + module + " rename to public");
        }
        c.createStatement().execute("create schema if not exists public");
        Flyway.configure()
            .dataSource(dataSource)
            .locations(String.format("classpath:/%s/db/migration", module))
            .load()
            .migrate();
        c.createStatement().execute(
            String.format("alter schema public rename to %s;create schema public;", module)
        );
      }
    }
  }

  // Replace the default migration strategy with our own.
  @Override
  public void migrate(Flyway flyway) {
  }
}
