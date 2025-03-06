package uk.gov.hmcts.reform.roleassignment.launchdarkly;

import com.launchdarkly.shaded.org.jetbrains.annotations.NotNull;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import uk.gov.hmcts.reform.roleassignment.controller.advice.exception.ForbiddenException;
import uk.gov.hmcts.reform.roleassignment.controller.advice.exception.ResourceNotFoundException;
import uk.gov.hmcts.reform.roleassignment.util.SecurityUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
@AllArgsConstructor
public class FeatureConditionEvaluation implements HandlerInterceptor {

    @Autowired
    private final FeatureToggleService featureToggleService;

    @Autowired
    private final SecurityUtils securityUtils;

    @Override
    public boolean preHandle(@NotNull HttpServletRequest request,
                             @NotNull HttpServletResponse response, @NotNull Object arg2) throws Exception {

        var flagName = featureToggleService.getLaunchDarklyFlag(request);

        if (flagName == null) {
            throw new ForbiddenException("The endpoint is not configured in Launch Darkly");
        }

        if (!featureToggleService.isValidFlag(flagName)) {
            throw new ResourceNotFoundException(String.format(
                "The flag %s is not configured in Launch Darkly", flagName));
        }

        var flagStatus = featureToggleService.isFlagEnabled(securityUtils.getServiceName(), flagName);
        if (!flagStatus) {
            throw new ForbiddenException(String.format("Launch Darkly flag is not enabled for the endpoint %s",
                                                       request.getRequestURI()));
        }
        return flagStatus;
    }

}
