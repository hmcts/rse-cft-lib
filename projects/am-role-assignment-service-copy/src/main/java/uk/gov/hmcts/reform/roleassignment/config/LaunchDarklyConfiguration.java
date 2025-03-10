package uk.gov.hmcts.reform.roleassignment.config;

import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.interfaces.LDClientInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import uk.gov.hmcts.reform.roleassignment.launchdarkly.FeatureConditionEvaluation;

@Configuration
public class LaunchDarklyConfiguration implements WebMvcConfigurer {

    @Value("${launchdarkly.runOnStartup:true}")
    private boolean runOnStartup;

    @Bean
    public LDClientInterface ldClient(@Value("${launchdarkly.sdk.key}") String sdkKey) {
        return runOnStartup ? new LDClient(sdkKey) : new LDDummyClient();
    }

    @Autowired
    private FeatureConditionEvaluation featureConditionEvaluation;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //Any new end point need to be placed in respective map.
        //registry.addInterceptor(featureConditionEvaluation).addPathPatterns("/am/role-assignments")
        registry.addInterceptor(featureConditionEvaluation).addPathPatterns("/am/role-assignments/createFeatureFlag");
        registry.addInterceptor(featureConditionEvaluation).addPathPatterns("/am/role-assignments/fetchFlagStatus");
        registry.addInterceptor(featureConditionEvaluation).addPathPatterns("/am/role-assignments/query/delete");
    }
}

