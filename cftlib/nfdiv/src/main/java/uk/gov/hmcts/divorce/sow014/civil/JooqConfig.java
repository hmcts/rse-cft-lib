package uk.gov.hmcts.divorce.sow014.civil;

import org.jooq.impl.DefaultConfiguration;
import org.springframework.boot.autoconfigure.jooq.DefaultConfigurationCustomizer;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JooqConfig implements DefaultConfigurationCustomizer {

    @Override
    public void customize(DefaultConfiguration configuration) {
        configuration.settings().withExecuteWithOptimisticLocking(true);
    }
}
