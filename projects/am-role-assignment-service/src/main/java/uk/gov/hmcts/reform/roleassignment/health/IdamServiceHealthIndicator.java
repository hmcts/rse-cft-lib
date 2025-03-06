package uk.gov.hmcts.reform.roleassignment.health;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class IdamServiceHealthIndicator
    implements HealthIndicator, HealthContributor, BaseHealthIndicator {

    private RestTemplate restTemplate;
    @Value("${idam.api.url:}") String serviceUrl;

    @Autowired
    public IdamServiceHealthIndicator(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public Health health() {
        return checkServiceHealth(restTemplate, serviceUrl);
    }
}
