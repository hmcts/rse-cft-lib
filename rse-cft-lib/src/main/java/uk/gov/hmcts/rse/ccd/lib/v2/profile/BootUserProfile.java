package uk.gov.hmcts.rse.ccd.lib.v2.profile;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import uk.gov.hmcts.ccd.userprofile.UserProfileApplication;
import uk.gov.hmcts.ccd.userprofile.SwaggerConfiguration;
import uk.gov.hmcts.ccd.userprofile.auth.AuthorizedConfiguration;
import uk.gov.hmcts.ccd.userprofile.auth.AuthCheckerConfiguration;
import uk.gov.hmcts.ccd.userprofile.endpoint.userprofile.UserProfileEndpoint;
import uk.gov.hmcts.reform.idam.client.IdamApi;
import uk.gov.hmcts.rse.ccd.lib.common.DBWaiter;
import uk.gov.hmcts.rse.ccd.lib.common.SecurityConfiguration;

@ComponentScan(
    basePackageClasses = {
        UserProfileApplication.class,
        DBWaiter.class
    },
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
        UserProfileApplication.class,
        SwaggerConfiguration.class,
        // Don't apply our custom security config.
        SecurityConfiguration.class
//        AuthorizedConfiguration.class,
//        AuthCheckerConfiguration.class,
    }))
@PropertySources({
    @PropertySource("classpath:userprofile/application.properties"),
    @PropertySource("classpath:rse/userprofile.properties"),
    @PropertySource("classpath:rse/application.properties"),
})
@EntityScan(basePackages = {
    "uk.gov.hmcts.ccd.userprofile"
})
@EnableJpaRepositories(basePackages = {
    "uk.gov.hmcts.ccd.userprofile"
})
@EnableFeignClients(
    clients = {
        IdamApi.class,
    })
@SpringBootConfiguration
@EnableAutoConfiguration
public class BootUserProfile {

}
