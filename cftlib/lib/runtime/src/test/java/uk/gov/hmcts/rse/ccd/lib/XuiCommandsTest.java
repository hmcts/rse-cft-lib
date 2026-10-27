package uk.gov.hmcts.rse.ccd.lib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

class XuiCommandsTest {

    private static final String SHA = "4d344f6ff7815ecf507a9db38667be6a7cf8d375";
    private static final String URL = "https://github.com/hmcts/rpx-xui-webapp.git";

    private final Path dir = Path.of("/tmp/cftlib/xui/manage-cases");

    @Test
    void shallowClonesABranchOrTagDirectly() {
        assertThat(XuiCommands.cloneCommands(URL, "master", dir)).containsExactly(
            List.of("git", "clone", "--depth", "1", "--branch", "master", URL, dir.toString()));
    }

    @Test
    void fetchesAPinnedShaBecauseCloneBranchCannotTakeOne() {
        assertThat(XuiCommands.cloneCommands(URL, SHA, dir)).containsExactly(
            List.of("git", "init", "--quiet", dir.toString()),
            List.of("git", "-C", dir.toString(), "remote", "add", "origin", URL),
            List.of("git", "-C", dir.toString(), "fetch", "--depth", "1", "origin", SHA),
            List.of("git", "-C", dir.toString(), "checkout", "--quiet", "--detach", "FETCH_HEAD"));
    }

    @Test
    void movesAnExistingCloneOntoTheRef() {
        assertThat(XuiCommands.fetchCommands(URL, "master", dir)).containsExactly(
            List.of("git", "-C", dir.toString(), "remote", "set-url", "origin", URL),
            List.of("git", "-C", dir.toString(), "fetch", "--depth", "1", "origin", "master"),
            List.of("git", "-C", dir.toString(), "checkout", "--quiet", "--detach", "FETCH_HEAD"));
    }

    @Test
    void recognisesACommitSha() {
        assertThat(XuiCommands.isCommitSha(SHA)).isTrue();
        assertThat(XuiCommands.isCommitSha(SHA.toUpperCase(Locale.ROOT))).isTrue();
        assertThat(XuiCommands.isCommitSha("master")).isFalse();
        assertThat(XuiCommands.isCommitSha("4d344f6")).isFalse();
        assertThat(XuiCommands.isCommitSha("v7.0.16")).isFalse();
    }

    @Test
    void readsTheHeadOfACheckout() {
        assertThat(XuiCommands.headCommand(dir))
            .containsExactly("git", "-C", dir.toString(), "rev-parse", "HEAD");
    }

    @Test
    void runsTheVendoredYarnReleaseWithNode() {
        var release = dir.resolve(".yarn/releases/yarn-4.17.1.cjs");

        assertThat(XuiCommands.yarn("node", release, XuiApp.MANAGE_CASES.installArgs()))
            .containsExactly("node", release.toString(), "install", "--immutable", "--inline-builds");
        assertThat(XuiCommands.yarn("node", release, XuiApp.MANAGE_ORG.installArgs()))
            .containsExactly("node", release.toString(), "install", "--immutable", "--mode=skip-build");
        assertThat(XuiCommands.yarn("/opt/node/bin/node", release, List.of("build")))
            .containsExactly("/opt/node/bin/node", release.toString(), "build");
    }

    @Test
    void startsTheApiBundleEachAppBuilds() {
        assertThat(XuiCommands.startCommand("node", XuiApp.MANAGE_CASES))
            .containsExactly("node", "./dist/rpx-exui/api/server.bundle.js");
        assertThat(XuiCommands.startCommand("node", XuiApp.MANAGE_ORG))
            .containsExactly("node", "--enable-source-maps",
                "./dist/rpx-xui-manage-organisations/api/server.bundle.js");
    }

    @Test
    void findsTheVendoredYarnReleaseFromTheYarnrc() {
        var yarnrc = """
            compressionLevel: mixed

            enableGlobalCache: false

            nodeLinker: node-modules

            yarnPath: .yarn/releases/yarn-4.17.1.cjs
            """;

        assertThat(XuiCommands.yarnPath(yarnrc)).isEqualTo(".yarn/releases/yarn-4.17.1.cjs");
        assertThat(XuiCommands.yarnPath("yarnPath: \".yarn/releases/yarn-4.9.2.cjs\"\n"))
            .isEqualTo(".yarn/releases/yarn-4.9.2.cjs");
    }

    @Test
    void failsWhenTheCheckoutVendorsNoYarn() {
        assertThatThrownBy(() -> XuiCommands.yarnPath("nodeLinker: node-modules\n"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("yarnPath");
    }

    @Test
    void installSkipsEveryBrowserDownloadTheDockerfilesSkip() {
        assertThat(XuiCommands.INSTALL_ENVIRONMENT).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
            Map.entry("PUPPETEER_SKIP_DOWNLOAD", "1"),
            Map.entry("PUPPETEER_SKIP_CHROMIUM_DOWNLOAD", "1"),
            Map.entry("CHROMEDRIVER_SKIP_DOWNLOAD", "1"),
            Map.entry("CYPRESS_INSTALL_BINARY", "0"),
            Map.entry("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1"),
            Map.entry("PLAYWRIGHT_BROWSERS_PATH", "0"),
            Map.entry("SENTRYCLI_SKIP_DOWNLOAD", "1"),
            Map.entry("NPM_CONFIG_FUND", "false"),
            Map.entry("NPM_CONFIG_AUDIT", "false"),
            Map.entry("NPM_CONFIG_UPDATE_NOTIFIER", "false"),
            Map.entry("SCARF_ANALYTICS", "false")));
    }

    @Test
    void theProcessEnvironmentIsTheComputedSetPlusAnAllowlistOfHostVariables() {
        var host = Map.of(
            "PATH", "/usr/bin",
            "HOME", "/home/dev",
            "NODE_CONFIG_ENV", "aat",
            "LOGGING", "silly",
            "JVM_HOST", "10.0.0.1",
            "RSE_LIB_AUTH-MODE", "localAuth");
        var computed = Map.of("PORT", "3000", "LOGGING", "debug");

        var environment = XuiCommands.processEnvironment(host, computed);

        assertThat(environment).containsExactlyInAnyOrderEntriesOf(Map.of(
            "PATH", "/usr/bin",
            "HOME", "/home/dev",
            "PORT", "3000",
            "LOGGING", "debug"));
    }
}
