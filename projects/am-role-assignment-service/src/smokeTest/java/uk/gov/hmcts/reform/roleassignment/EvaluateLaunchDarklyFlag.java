/*

This is a Junit 5 class. Will remove the old Junit 4 once we migrate to this class.

package uk.gov.hmcts.reform.roleassignment;

import java.io.IOException;
import java.util.Optional;

import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.server.LDClient;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assume;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

//This class will be used when we migrate to Junit 5

@Slf4j
class EvaluateLaunchDarklyFlag implements ExecutionCondition {

    private static final String MICROSERVICE_NAME = "am_role_assignment_service";

    private static final ConditionEvaluationResult ENABLED = ConditionEvaluationResult
        .enabled("Feature Flag is enabled");

    private static final ConditionEvaluationResult DISABLED = ConditionEvaluationResult
        .disabled("Feature Flag is disabled");

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        SmokeTest test = (SmokeTest) context.getTestInstance().get();

        boolean isFlagEnabled = false;

        Optional<LaunchDarklyFlagEvaluator> flagName = AnnotationSupport
            .findAnnotation(context.getElement(), LaunchDarklyFlagEvaluator.class);

        if (flagName.isPresent()) {
            try (LDClient client = new LDClient(test.sdkKey)) {

                LDUser user = new LDUser.Builder(test.environment)
                    .firstName(test.userName)
                    .lastName("user")
                    .custom("servicename", MICROSERVICE_NAME)
                    .build();

                isFlagEnabled = client.boolVariation(flagName.get().value(), user, false);
            } catch (IOException exception) {
                log.warn("Error getting Launch Darkly connection in Smoke tests");
            }
        }

        return isFlagEnabled ? ENABLED : DISABLED;
    }
}
*/
