package uk.gov.hmcts.reform.roleassignment.config;

import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.StatelessKieSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DroolConfig {

    private KieServices kieServices = KieServices.Factory.get();

    @Bean
    public KieContainer kieContainer() {
        return kieServices.getKieClasspathContainer();
    }

    @Bean
    public StatelessKieSession kieSession() {
        return kieContainer().newStatelessKieSession("role-assignment-validation-session");
    }


}
