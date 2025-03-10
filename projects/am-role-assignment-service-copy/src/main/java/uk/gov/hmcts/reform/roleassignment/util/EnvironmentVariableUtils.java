package uk.gov.hmcts.reform.roleassignment.util;

import uk.gov.hmcts.reform.roleassignment.controller.advice.exception.ResourceNotFoundException;

public class EnvironmentVariableUtils {
    private EnvironmentVariableUtils() {

    }

    public static String getRequiredVariable(String name) {
        if (System.getenv(name) == null) {
            throw new ResourceNotFoundException(String.format("Environment variable %s not found", name));
        }
        return System.getenv(name);
    }
}
