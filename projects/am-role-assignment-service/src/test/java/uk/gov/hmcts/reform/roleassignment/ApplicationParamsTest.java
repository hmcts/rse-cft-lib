package uk.gov.hmcts.reform.roleassignment;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ApplicationParamsTest {

    private ApplicationParams applicationParams = new ApplicationParams();

    @Test
    void shouldGetAuditLogEnabled() {
        List<Integer> statusCodes = List.of(404);
        ReflectionTestUtils.setField(applicationParams, "auditLogIgnoreStatuses", statusCodes);
        List<Integer> result = applicationParams.getAuditLogIgnoreStatuses();
        assertNotNull(result);
        assertThat(result).isEqualTo(statusCodes);

    }

    @Test
    void shouldCheckAuditLogEnabled() {
        ReflectionTestUtils.setField(applicationParams, "auditLogEnabled", true);
        Boolean result = applicationParams.isAuditLogEnabled();
        assertNotNull(result);
        assertThat(result).isEqualTo(Boolean.TRUE);

    }

    @Test
    void shouldCheckWhenAuditLogNotEnabled() {
        ReflectionTestUtils.setField(applicationParams, "auditLogEnabled", false);
        Boolean result = applicationParams.isAuditLogEnabled();
        assertNotNull(result);
        assertThat(result).isEqualTo(Boolean.FALSE);

    }

}
