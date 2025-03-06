package uk.gov.hmcts.divorce.client.request;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

public enum RoleType {
    CASE, ORGANISATION, @JsonEnumDefaultValue UNKNOWN
}
