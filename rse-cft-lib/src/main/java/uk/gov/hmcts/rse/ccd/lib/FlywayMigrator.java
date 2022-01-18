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
@Getter
public class FlywayMigrator implements FlywayMigrationStrategy {

  @Autowired
  DataSource dataStore;

  private Boolean didCleanMigration;

  /**
   * @return True for a clean migration, false otherwise
   */
  @SneakyThrows
  @PostConstruct
  public boolean migrate() {
    // TODO: DB per service with regular migrations.
    try (Connection c = dataStore.getConnection()) {
      var rs = c.createStatement().executeQuery(
          "SELECT schema_name FROM information_schema.schemata WHERE schema_name = 'datastore'"
      );
      if (rs.next()) {
        log.info("Skipping DB migrations in existing DB");
        this.didCleanMigration = false;
        return false;
      }
    }

    // Run data and definition store migrations.
    // Many of the CCD migrations use the fully qualified public schema name
    // so we rename the public schema each time.
    for (String module : List.of(
        "datastore",
        "definitionstore",
        "userprofile",
        "am"
    )) {
      Flyway.configure()
          .dataSource(dataStore)
          .locations(String.format("classpath:/%s/db/migration", module))
          .load()
          .migrate();
      try (Connection c = dataStore.getConnection()) {
        c.createStatement().execute(
            String.format("alter schema public rename to %s;create schema public;", module)
        );
      }
    }
    this.didCleanMigration = true;
    return true;
  }

  // Replace the default migration strategy with our own.
  @Override
  public void migrate(Flyway flyway) {
  }
}
