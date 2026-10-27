package uk.gov.hmcts.rse.ccd.lib;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class XuiAppTest {

    @Test
    void bothAppsArePinnedToAReproducibleRevision() {
        for (var app : XuiApp.values()) {
            assertThat(app.repoUrl(Map.of())).startsWith("https://github.com/hmcts/");
            assertThat(XuiCommands.isCommitSha(app.ref(Map.of())))
                .as("%s should be pinned to a commit sha", app.appName())
                .isTrue();
            assertThat(app.nodeRange(Map.of())).startsWith(">=");
        }
    }

    @Test
    void thePinnedNodeRangesMatchThePackageJsonOfThePinnedRevisions() {
        assertThat(XuiApp.MANAGE_CASES.nodeRange(Map.of())).isEqualTo(">=24.22.1");
        assertThat(XuiApp.MANAGE_ORG.nodeRange(Map.of())).isEqualTo(">=24.22.1 <25");
    }

    @Test
    void everyPinCanBeOverriddenPerTask() {
        var source = Map.of(
            "RSE_LIB_XUI_REPO", "git@github.com:me/rpx-xui-webapp.git",
            "RSE_LIB_XUI_REF", "my-branch",
            "RSE_LIB_XUI_NODE_RANGE", ">=22",
            "RSE_LIB_XUI_MO_REPO", "git@github.com:me/rpx-xui-manage-organisations.git",
            "RSE_LIB_XUI_MO_REF", "my-other-branch",
            "RSE_LIB_XUI_MO_NODE_RANGE", ">=23");

        assertThat(XuiApp.MANAGE_CASES.repoUrl(source)).isEqualTo("git@github.com:me/rpx-xui-webapp.git");
        assertThat(XuiApp.MANAGE_CASES.ref(source)).isEqualTo("my-branch");
        assertThat(XuiApp.MANAGE_CASES.nodeRange(source)).isEqualTo(">=22");
        assertThat(XuiApp.MANAGE_ORG.repoUrl(source))
            .isEqualTo("git@github.com:me/rpx-xui-manage-organisations.git");
        assertThat(XuiApp.MANAGE_ORG.ref(source)).isEqualTo("my-other-branch");
        assertThat(XuiApp.MANAGE_ORG.nodeRange(source)).isEqualTo(">=23");
    }

    @Test
    void blankOverrideFallsBackToThePin() {
        assertThat(XuiApp.MANAGE_CASES.ref(Map.of("RSE_LIB_XUI_REF", "  ")))
            .isEqualTo(XuiApp.MANAGE_CASES.ref(Map.of()));
    }

    @Test
    void anExistingCheckoutCanBeUsedInsteadOfTheCache() {
        assertThat(XuiApp.MANAGE_CASES.checkoutOverride(Map.of())).isNull();
        assertThat(XuiApp.MANAGE_CASES.checkoutOverride(Map.of("RSE_LIB_XUI_DIR", " /src/rpx-xui-webapp ")))
            .isEqualTo("/src/rpx-xui-webapp");
        assertThat(XuiApp.MANAGE_ORG.checkoutOverride(Map.of("RSE_LIB_XUI_MO_DIR", "/src/mo")))
            .isEqualTo("/src/mo");
        assertThat(XuiApp.MANAGE_ORG.checkoutOverride(Map.of("RSE_LIB_XUI_DIR", "/src/rpx-xui-webapp"))).isNull();
    }

    @Test
    void theManageCasesPinIsNotSharedWithManageOrg() {
        assertThat(XuiApp.MANAGE_CASES.repoUrl(Map.of())).isNotEqualTo(XuiApp.MANAGE_ORG.repoUrl(Map.of()));
        assertThat(XuiApp.MANAGE_CASES.ref(Map.of())).isNotEqualTo(XuiApp.MANAGE_ORG.ref(Map.of()));
    }
}
