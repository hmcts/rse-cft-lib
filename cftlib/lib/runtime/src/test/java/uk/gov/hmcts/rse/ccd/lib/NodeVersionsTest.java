package uk.gov.hmcts.rse.ccd.lib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NodeVersionsTest {

    @Test
    void readsAPartialVersionAsTheRangeItStandsFor() {
        // npm: '24' admits every 24.x, and does not admit 25.
        assertThat(NodeVersions.satisfies("v24.22.1", "24")).isTrue();
        assertThat(NodeVersions.satisfies("v24.0.0", "24")).isTrue();
        assertThat(NodeVersions.satisfies("v25.0.0", "24")).isFalse();
        assertThat(NodeVersions.satisfies("v23.9.9", "24")).isFalse();

        assertThat(NodeVersions.satisfies("v24.22.5", "24.22")).isTrue();
        assertThat(NodeVersions.satisfies("v24.23.0", "24.22")).isFalse();
    }

    @Test
    void boundsAPartialVersionAtTheEndOfItsRange() {
        // '>24' means after every 24.x, '<=24' through all of them.
        assertThat(NodeVersions.satisfies("v24.22.1", ">24")).isFalse();
        assertThat(NodeVersions.satisfies("v25.0.0", ">24")).isTrue();

        assertThat(NodeVersions.satisfies("v24.99.0", "<=24")).isTrue();
        assertThat(NodeVersions.satisfies("v25.0.0", "<=24")).isFalse();

        assertThat(NodeVersions.satisfies("v24.0.0", ">=24")).isTrue();
        assertThat(NodeVersions.satisfies("v24.0.0", "<24")).isFalse();
    }

    @Test
    void acceptsAVersionAtOrAboveTheFloor() {
        assertThat(NodeVersions.satisfies("v24.22.1", ">=24.22.1")).isTrue();
        assertThat(NodeVersions.satisfies("v24.22.2", ">=24.22.1")).isTrue();
        assertThat(NodeVersions.satisfies("v25.0.0", ">=24.22.1")).isTrue();
        assertThat(NodeVersions.satisfies("v24.23.0", ">=24.22.1")).isTrue();
    }

    @Test
    void rejectsAVersionBelowTheFloor() {
        assertThat(NodeVersions.satisfies("v24.21.0", ">=24.22.1")).isFalse();
        assertThat(NodeVersions.satisfies("v24.22.0", ">=24.22.1")).isFalse();
        assertThat(NodeVersions.satisfies("v22.20.0", ">=24.22.1")).isFalse();
    }

    @Test
    void appliesEveryClauseOfARange() {
        assertThat(NodeVersions.satisfies("v24.22.1", ">=24.22.1 <25")).isTrue();
        assertThat(NodeVersions.satisfies("v24.99.99", ">=24.22.1 <25")).isTrue();
        assertThat(NodeVersions.satisfies("v25.0.0", ">=24.22.1 <25")).isFalse();
        assertThat(NodeVersions.satisfies("v24.21.0", ">=24.22.1 <25")).isFalse();
    }

    @Test
    void ignoresAPreReleaseSuffix() {
        assertThat(NodeVersions.satisfies("v25.0.0-nightly20260101", "<25")).isFalse();
        assertThat(NodeVersions.satisfies("v24.23.0-nightly20260101", ">=24.22.1 <25")).isTrue();
    }

    @Test
    void supportsTheRemainingComparators() {
        assertThat(NodeVersions.satisfies("v24.22.1", ">24.22.0")).isTrue();
        assertThat(NodeVersions.satisfies("v24.22.1", ">24.22.1")).isFalse();
        assertThat(NodeVersions.satisfies("v24.22.1", "<=24.22.1")).isTrue();
        assertThat(NodeVersions.satisfies("v24.22.1", "24.22.1")).isTrue();
        assertThat(NodeVersions.satisfies("v24.22.2", "24.22.1")).isFalse();
    }

    @Test
    void namesTheRequiredAndFoundVersionWhenRejecting() {
        assertThatThrownBy(() -> NodeVersions.require("xui-manage-cases", ">=24.22.1", "v24.21.0"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("xui-manage-cases")
            .hasMessageContaining(">=24.22.1")
            .hasMessageContaining("v24.21.0");
    }

    @Test
    void acceptsTheHostNodeWhenItSatisfiesTheRange() {
        NodeVersions.require("xui-manage-cases", ">=24.22.1", "v24.22.1");
    }

    @Test
    void readsTheEnginesRangeFromAPackageJson() {
        var manageCases = """
            {
              "name": "rpx-exui",
              "type": "commonjs",
              "engines": {
                "node": ">=24.22.1"
              }
            }
            """;
        var manageOrg = """
            {
              "name": "rpx-xui-manage-organisations",
              "engines": { "node": ">=24.22.1 <25" },
              "workspaces": ["api"]
            }
            """;

        assertThat(NodeVersions.enginesNode(manageCases)).isEqualTo(">=24.22.1");
        assertThat(NodeVersions.enginesNode(manageOrg)).isEqualTo(">=24.22.1 <25");
        assertThat(NodeVersions.enginesNode("{\"name\": \"no-engines\"}")).isNull();
    }

    @Test
    void rejectsAnUnsupportedRange() {
        assertThatThrownBy(() -> NodeVersions.satisfies("v24.22.1", "^24.22.1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("^24.22.1");
    }
}
