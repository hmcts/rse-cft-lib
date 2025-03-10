package uk.gov.hmcts.reform.roleassignment.config;

import com.google.common.collect.ImmutableSet;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Configuration
@ConfigurationProperties(prefix = "security")
@Slf4j
public class AuthCheckerConfiguration {

    List<String> authorisedServices;

    List<String> authorisedRoles;

    public List<String> getAuthorisedServices() {
        return authorisedServices;
    }

    public void setAuthorisedServices(List<String> authorisedServices) {
        this.authorisedServices = authorisedServices;
    }

    public List<String> getAuthorisedRoles() {
        return authorisedRoles;
    }

    public void setAuthorisedRoles(List<String> authorisedRoles) {
        this.authorisedRoles = authorisedRoles;
    }

    @Bean
    public Function<HttpServletRequest, Collection<String>> authorizedServicesExtractor() {
        return any -> ImmutableSet.copyOf(authorisedServices);
    }

    @Bean
    public Function<HttpServletRequest, Collection<String>> authorizedRolesExtractor() {
        return any -> ImmutableSet.copyOf(authorisedRoles);
    }

    @Bean
    public Function<HttpServletRequest, Optional<String>> userIdExtractor() {
        return any -> Optional.empty();
    }

}
