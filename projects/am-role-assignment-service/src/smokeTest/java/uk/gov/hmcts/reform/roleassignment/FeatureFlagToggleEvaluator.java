package uk.gov.hmcts.reform.roleassignment;

import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.server.LDClient;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.junit.Assume;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.IOException;

@Slf4j
public class FeatureFlagToggleEvaluator implements TestRule {

    public static final String USER = "user";
    public static final String SERVICENAME = "servicename";
    public static final String AM_ROLE_ASSIGNMENT_SERVICE = "am_role_assignment_service";

    private final SmokeTest smokeTest;

    public FeatureFlagToggleEvaluator(SmokeTest smokeTest) {
        this.smokeTest = smokeTest;
    }

    @SneakyThrows
    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                boolean isFlagEnabled = false;
                String message = "The test is ignored as LD flag is false";

                FeatureFlagToggle featureFlagToggle = description.getAnnotation(FeatureFlagToggle.class);

                if (featureFlagToggle != null) {
                    if (StringUtils.isNotEmpty(featureFlagToggle.value())) {
                        try (LDClient client = new LDClient(smokeTest.getSdkKey())) {

                            LDUser user = new LDUser.Builder(smokeTest.getEnvironment())
                                .firstName(smokeTest.getUserName())
                                .lastName(USER)
                                .custom(SERVICENAME, AM_ROLE_ASSIGNMENT_SERVICE)
                                .build();

                            if (!client.isFlagKnown(featureFlagToggle.value())) {
                                message = String.format("The flag %s is not registered with Launch Darkly",
                                    featureFlagToggle.value());
                            }
                            isFlagEnabled = client.boolVariation(featureFlagToggle.value(), user, false);
                        } catch (IOException exception) {
                            log.warn("Error getting Launch Darkly connection in Smoke tests");
                        }
                    }
                    Assume.assumeTrue(message, isFlagEnabled);
                }
                base.evaluate();
            }
        };
    }
}
