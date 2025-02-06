package uk.gov.hmcts.reform.roleassignment.health;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.actuate.health.Health;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.reform.roleassignment.controller.advice.exception.ResourceNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CcdDataStoreHealthIndicatorTest {

    private RestTemplate restTemplate = mock(RestTemplate.class);

    private CcdDataStoreHealthIndicator sut = new CcdDataStoreHealthIndicator(restTemplate);

    @BeforeEach
    void setUp() throws JsonProcessingException {
        MockitoAnnotations.openMocks(this);
        String jsonString = "{\"status\": \"UP\"}";
        ObjectMapper mapper = new ObjectMapper();
        JsonNode actualObj = mapper.readTree(jsonString);
        when(restTemplate.getForObject("url" + "/health", JsonNode.class)).thenReturn(actualObj);

    }

    @Test
    void checkPositiveServiceHealth() {
        Health health = sut.checkServiceHealth(restTemplate, "url");
        assertNotNull(health);
        assertEquals("UP", health.getStatus().getCode());
    }

    @Test
    void checkNegativeServiceHealth() {
        when(restTemplate.getForObject("url" + "/health", JsonNode.class))
            .thenThrow(ResourceNotFoundException.class);
        Health health = sut.checkServiceHealth(restTemplate, "url");
        assertNotNull(health);
        assertEquals("DOWN", health.getStatus().getCode());
    }

    @Test
    void getHealth() {
        sut.serviceUrl = "url";
        Health health = sut.health();
        assertNotNull(health);
        assertEquals("UP", health.getStatus().getCode());
    }

}
