package uk.gov.hmcts.reform.roleassignment.launchdarkly;

import com.launchdarkly.sdk.server.LDClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import javax.servlet.http.HttpServletRequest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
class FeatureToggleServiceTest {

    @Mock
    LDClient ldClient;

    @Mock
    HttpServletRequest request;

    @InjectMocks
    FeatureToggleService featureToggleService = new FeatureToggleService();

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void evaluateLdFlag() {
        when(ldClient.boolVariation(any(), any(), anyBoolean())).thenReturn(true);
        Assertions.assertTrue(featureToggleService.isFlagEnabled("serviceName", "userName"));
    }

    @Test
    void evaluateLdFlagFalse() {
        when(ldClient.boolVariation(any(), any(), anyBoolean())).thenReturn(false);
        Assertions.assertFalse(featureToggleService.isFlagEnabled("serviceName", "userName"));
    }

    @Test
    void isValidFlag() {
        when(ldClient.isFlagKnown(any())).thenReturn(true);
        Assertions.assertTrue(featureToggleService.isValidFlag("serviceName"));
    }

    @Test
    void isValidFlagReturnsFalse() {
        when(ldClient.isFlagKnown(any())).thenReturn(false);
        Assertions.assertFalse(featureToggleService.isValidFlag("serviceName"));
    }

    @ParameterizedTest
    @CsvSource({
        "/am/role-assignments/fetchFlagStatus,GET,get-db-drools-flag",
        "/am/role-assignments/createFeatureFlag,POST,get-db-drools-flag"
    })
    void getLdFlagWithValidFlagMapEntries(String url, String method, String flag) {
        when(request.getRequestURI()).thenReturn(url);
        when(request.getMethod()).thenReturn(method);
        String flagName = featureToggleService.getLaunchDarklyFlag(request);
        Assertions.assertEquals(flag, flagName);
    }

    @ParameterizedTest
    @CsvSource({
        "/am/role-assignments/createFeatureFlag,POST,get-db-drools-flag"
    })
    void getLdFlagGet_RoleAssignmentCase(String url, String method, String flag) {
        when(request.getRequestURI()).thenReturn(url);
        when(request.getMethod()).thenReturn(method);
        String flagName = featureToggleService.getLaunchDarklyFlag(request);
        Assertions.assertEquals(flag, flagName);
    }

    @ParameterizedTest
    @CsvSource({
        "GET",
        "DELETE",
        "POST",
        "POST",
        "INVALID",
    })
    void getLdFlagWithNonExistingUriPath(String method) {
        when(request.getRequestURI()).thenReturn("/am/dummy");
        when(request.getMethod()).thenReturn(method);
        String flagName = featureToggleService.getLaunchDarklyFlag(request);
        Assertions.assertNull(flagName);
    }

    @Test
    void getLdFlagDeleteStringContainsCase() {
        when(request.getRequestURI()).thenReturn("/am/role-assignments/");
        when(request.getMethod()).thenReturn("DELETE");
        String flagName = featureToggleService.getLaunchDarklyFlag(request);
        Assertions.assertNull(flagName);
    }
}
