package uk.gov.hmcts.reform.roleassignment.launchdarkly;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.roleassignment.controller.advice.exception.ForbiddenException;
import uk.gov.hmcts.reform.roleassignment.controller.advice.exception.ResourceNotFoundException;
import uk.gov.hmcts.reform.roleassignment.util.SecurityUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
class FeatureConditionEvaluationTest {

    FeatureToggleService featureToggleService = mock(FeatureToggleService.class);

    SecurityUtils securityUtils = mock(SecurityUtils.class);

    HttpServletRequest request = mock(HttpServletRequest.class);

    HttpServletResponse response = mock(HttpServletResponse.class);

    Map<String, String> launchDarklyMap;

    Object object = new Object();

    @InjectMocks
    FeatureConditionEvaluation featureConditionEvaluation = new FeatureConditionEvaluation(
        featureToggleService,
        securityUtils
    );

    @BeforeEach
    void initializeMocks() {
        MockitoAnnotations.openMocks(this);
        launchDarklyMap = new HashMap<>();
        launchDarklyMap.put("/am/role-assignments/ld/endpoint", "get-ld-flag");
    }

    @Test
    void getPositiveResponseForFlag() throws Exception {
        when(request.getRequestURI()).thenReturn("/am/role-assignments/ld/endpoint");
        when(request.getMethod()).thenReturn("GET");
        when(featureToggleService.getLaunchDarklyFlag(any())).thenReturn("get-ld-flag");
        when(featureToggleService.isFlagEnabled(any(), any())).thenReturn(true);
        when(featureToggleService.isValidFlag(any())).thenReturn(true);
        Assertions.assertTrue(featureConditionEvaluation.preHandle(request, response, new Object()));
    }

    @Test
    void getNegativeResponseForFlag() {
        when(request.getRequestURI()).thenReturn("/am/role-assignments/ld/endpoint");
        when(featureToggleService.isValidFlag(any())).thenReturn(true);
        when(featureToggleService.isFlagEnabled(any(), any())).thenReturn(false);
        Assertions.assertThrows(ForbiddenException.class, () ->
            featureConditionEvaluation.preHandle(request, response, object)
        );
    }

    @Test
    void expectExceptionForNonRegisteredURI() {
        when(request.getRequestURI()).thenReturn("");
        Assertions.assertThrows(ForbiddenException.class, () ->
            featureConditionEvaluation.preHandle(request, response, object)
        );
    }

    @Test
    void expectExceptionForInvalidFlagName() {
        when(request.getRequestURI()).thenReturn("/am/role-assignments/ld/endpoint");
        when(request.getMethod()).thenReturn("GET");
        when(featureToggleService.getLaunchDarklyFlag(any())).thenReturn("get-ld-flag");
        when(featureToggleService.isValidFlag(any())).thenReturn(false);
        Assertions.assertThrows(ResourceNotFoundException.class, () ->
            featureConditionEvaluation.preHandle(request, response, object)
        );
    }

    @Test
    void expectExceptionForFlagDiable() {
        when(request.getRequestURI()).thenReturn("/am/role-assignments/ld/endpoint");
        when(request.getMethod()).thenReturn("GET");
        when(featureToggleService.getLaunchDarklyFlag(any())).thenReturn("get-ld-flag");
        when(featureToggleService.isFlagEnabled(any(), any())).thenReturn(false);
        when(featureToggleService.isValidFlag(any())).thenReturn(true);
        Assertions.assertThrows(ForbiddenException.class, () ->
            featureConditionEvaluation.preHandle(request, response, object)
        );
    }
}
