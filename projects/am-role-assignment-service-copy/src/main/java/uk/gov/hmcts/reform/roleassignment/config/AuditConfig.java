package uk.gov.hmcts.reform.roleassignment.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import uk.gov.hmcts.reform.roleassignment.ApplicationParams;
import uk.gov.hmcts.reform.roleassignment.auditlog.AuditInterceptor;
import uk.gov.hmcts.reform.roleassignment.auditlog.AuditService;

@Configuration
public class AuditConfig implements WebMvcConfigurer {

    @Autowired
    private AuditService auditService;

    @Autowired
    private ApplicationParams applicationParams;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auditInterceptor());
    }

    @Bean
    public AuditInterceptor auditInterceptor() {
        return new AuditInterceptor(auditService, applicationParams);
    }

}
