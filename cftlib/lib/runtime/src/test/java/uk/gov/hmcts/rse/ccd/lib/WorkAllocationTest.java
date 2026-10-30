package uk.gov.hmcts.rse.ccd.lib;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class WorkAllocationTest {

    @Test
    void systemUserHasWorkAllocationsRoles() {
        assertThat(WorkAllocation.systemUserRoles(Map.of()))
            .containsExactly("caseworker", "caseworker-wa", "caseworker-wa-task-configuration");
    }

    @Test
    void systemUserAlsoHasTheServicesRoles() {
        assertThat(WorkAllocation.systemUserRoles(Map.of("RSE_LIB_WA_SYSTEM_USER_ROLES",
            " caseworker-civil, caseworker-civil-systemupdate,,caseworker-wa")))
            .containsExactly("caseworker", "caseworker-wa", "caseworker-wa-task-configuration", "caseworker-civil",
                "caseworker-civil-systemupdate");
    }
}
