package uk.gov.hmcts.divorce.sow014.civil;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class JooqConfig {

    public DSLContext db(DataSource dataSource) {
        return DSL.using(dataSource, SQLDialect.POSTGRES, new Settings().withExecuteWithOptimisticLocking(true));
    }
}
