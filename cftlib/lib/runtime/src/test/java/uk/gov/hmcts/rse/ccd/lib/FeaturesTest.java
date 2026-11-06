package uk.gov.hmcts.rse.ccd.lib;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class FeaturesTest {

    @Test
    void readsTheFeaturesTheRunTurnedOn() {
        assertThat(Features.enabled(Map.of("RSE_LIB_FEATURES", "wa, hmc,,servicebus ")))
            .containsExactlyInAnyOrder("wa", "hmc", "servicebus");
        assertThat(Features.enabled(Map.of("RSE_LIB_FEATURES", "wa, hmc"), "hmc")).isTrue();
        assertThat(Features.enabled(Map.of("RSE_LIB_FEATURES", "wa"), "hmc")).isFalse();
        assertThat(Features.enabled(Map.of())).isEmpty();
    }
}
