package uk.gov.hmcts.reform.roleassignment.health;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.actuate.health.Health;
import org.springframework.web.client.RestTemplate;

import java.util.Objects;

public interface BaseHealthIndicator {

    default Health checkServiceHealth(RestTemplate restTemplate, String url) {
        Health health = null;
        try {
            if (Objects.requireNonNull(restTemplate.getForObject(url + "/health", JsonNode.class))
                .get("status").asText().equalsIgnoreCase("UP")) {
                health = Health.up().build();
            }
        } catch (Exception ex) {
            health = Health.down(ex).build();
        }
        return health;
    }
}
