package uk.gov.hmcts.rse.ccd.lib.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "ccd.definition")
public class DefinitionConfig {
    private final Map<String, String> paths = new HashMap<>();

    @Bean(name = "paths")
    public Map<String, String> getPaths() {
        return paths;
    }
}
