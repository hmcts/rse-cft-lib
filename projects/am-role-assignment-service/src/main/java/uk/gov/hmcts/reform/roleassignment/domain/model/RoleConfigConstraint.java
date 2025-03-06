package uk.gov.hmcts.reform.roleassignment.domain.model;

import lombok.Value;

import java.util.Set;

@Value
public class RoleConfigConstraint<T> {

    private final boolean mandatory;
    private final Set<T> values;

    public boolean matches(T value) {
        if (mandatory) {
            return
                value != null && (values == null || values.contains(value));
        } else {
            return
                value == null || values == null || values.contains(value);
        }

    }

}
