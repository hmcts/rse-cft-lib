package uk.gov.hmcts.rse.ccd.lib;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class ComposeRunnerProfilesTest {

    @Test
    void readsTheProfilesDockerComposeStarts() {
        assertThat(ComposeRunner.composeProfiles(Map.of("COMPOSE_PROFILES", "localAuth, camunda")))
            .containsExactly("localAuth", "camunda");
    }

    @Test
    void startsNoOptionalProfilesByDefault() {
        assertThat(ComposeRunner.composeProfiles(Map.of())).isEmpty();
        assertThat(ComposeRunner.composeProfiles(Map.of("COMPOSE_PROFILES", ""))).isEmpty();
    }
}
