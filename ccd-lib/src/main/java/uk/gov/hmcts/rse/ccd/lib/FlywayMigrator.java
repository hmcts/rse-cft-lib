package uk.gov.hmcts.rse.ccd.lib;

import java.sql.Connection;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import lombok.SneakyThrows;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayMigrator implements FlywayMigrationStrategy {

  @Autowired
  DataSource dataStore;

  @SneakyThrows
  @PostConstruct
  public void migrate() {
    // Run data and definition store migrations.
    // Many of the CCD migrations use the fully qualified public schema name
    // so we rename the public schema each time.
    for (String module : List.of(
        "datastore",
        "definitionstore"
        ,"userprofile"
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
  }

  // Replace the default migration strategy with our own.
  @Override
  public void migrate(Flyway flyway) {
  }
}
