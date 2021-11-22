package uk.gov.hmcts.rse.ccd.lib;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayMigrator implements FlywayMigrationStrategy {

  @Autowired
  DataSource dataStore;

  @PostConstruct
  public void migrate() {
    // Run data and definition store migrations.
    // Many of the CCD migrations use the fully qualified public schema name
    // so the objects all end up in the same database, only the flyway history ends up in
    // different schemas.
    // TODO: This will break if CCD ever introduce colliding types in their different databases.
    // If so we will need to isolate the different CCD database migrations into their own schemas.
    // 1. Run data store migrations.
    // 2. Rename public schema to 'data' and recreate empty public
    // 3. Run def store migrations and rename public to 'def'
    // 4. Introduce a DataSource proxy that sets the appropriate schema based on the call stack.
    Flyway.configure()
        .dataSource(dataStore)
        .schemas("data")
        .locations("classpath:/data-store/db/migration")
        .load()
        .migrate();

    Flyway.configure()
        .dataSource(dataStore)
        .schemas("def")
        .locations("classpath:/definition-store/db/migration")
        .load()
        .migrate();
  }

  // Replace the default migration strategy with our own.
  @Override
  public void migrate(Flyway flyway) {
  }
}
