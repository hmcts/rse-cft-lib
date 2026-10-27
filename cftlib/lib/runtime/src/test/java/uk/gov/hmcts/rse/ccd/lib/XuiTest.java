package uk.gov.hmcts.rse.ccd.lib;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;

class XuiTest {

    @Test
    void adoptsAnInstanceThatIsAlreadyListening() throws Exception {
        try (var listening = new ServerSocket(0)) {
            // A checkout that does not exist, so anything beyond adoption would fail.
            var source = Map.of(
                "XUI_PORT", Integer.toString(listening.getLocalPort()),
                "RSE_LIB_XUI_DIR", "/cftlib/does/not/exist");

            assertThatCode(() -> Xui.manageCases().start(source)).doesNotThrowAnyException();
        }
    }

    @Test
    void refusesToBuildWhenTheHostNodeIsTooOld() throws Exception {
        var source = Map.of(
            "XUI_PORT", Integer.toString(freePort()),
            "RSE_LIB_XUI_NODE_RANGE", ">=999",
            "RSE_LIB_XUI_DIR", "/cftlib/does/not/exist");

        assertThatThrownBy(() -> Xui.manageCases().start(source))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(">=999");
    }

    @Test
    void namesTheNodeItFoundOnTheHost() throws Exception {
        var version = hostNodeVersion();
        assumeTrue(version != null, "no node on the path");
        var source = Map.of(
            "XUI_PORT", Integer.toString(freePort()),
            "RSE_LIB_XUI_NODE_RANGE", ">=999",
            "RSE_LIB_XUI_DIR", "/cftlib/does/not/exist");

        assertThatThrownBy(() -> Xui.manageCases().start(source))
            .hasMessageContaining(version);
    }

    @Test
    void stoppingSomethingThatWasNeverStartedDoesNothing() {
        assertThatCode(() -> Xui.manageOrg().stop()).doesNotThrowAnyException();
    }

    private static int freePort() throws IOException {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static String hostNodeVersion() {
        try {
            var process = new ProcessBuilder("node", "--version").redirectErrorStream(true).start();
            var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return process.waitFor() == 0 ? output : null;
        } catch (Exception e) {
            return null;
        }
    }
}
