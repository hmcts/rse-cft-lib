package uk.gov.hmcts.reform.roleassignment.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.roleassignment.controller.advice.exception.ResourceNotFoundException;

class EnvironmentVariableUtilsTest {

    @Test
    void getRequiredVariable() {
        String result = EnvironmentVariableUtils.getRequiredVariable("PATH");
        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.contains("/usr/"));
    }

    @Test
    void getNonExistingRequiredVariable() {
        Assertions.assertThrows(ResourceNotFoundException.class, () ->
            EnvironmentVariableUtils.getRequiredVariable("A_DUMMY_VARIABLE")
        );
    }

}
