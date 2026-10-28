package uk.gov.hmcts.rse.ccd.lib;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class XuiTest {

    @Test
    void reusesAnInstanceItLeftRunning(@TempDir Path cache) throws Exception {
        try (var listening = new ServerSocket(0)) {
            var port = listening.getLocalPort();
            // The pid of a live process on that port is what identifies it as ours; this JVM is
            // holding the socket, so its own pid is exactly that.
            var pidFile = Files.createDirectories(cache.resolve("xui")).resolve("manage-cases.pid");
            Files.writeString(pidFile, ProcessHandle.current().pid() + " " + port + " "
                + ProcessHandle.current().info().command().orElseThrow());

            // A checkout that does not exist, so anything beyond adoption would fail.
            var source = Map.of(
                "XUI_PORT", Integer.toString(port),
                "RSE_LIB_CACHE_DIR", cache.toString(),
                "RSE_LIB_XUI_DIR", "/cftlib/does/not/exist");

            assertThatCode(() -> Xui.manageCases().start(source)).doesNotThrowAnyException();
        }
    }

    @Test
    void refusesAPortWhoseRecordedPidIsNowSomethingElse(@TempDir Path cache) throws Exception {
        try (var listening = new ServerSocket(0)) {
            var port = listening.getLocalPort();
            // A live pid on the right port, but not the process that was recorded: a recycled pid
            // with a leftover container on the port is how this arises.
            var pidFile = Files.createDirectories(cache.resolve("xui")).resolve("manage-cases.pid");
            Files.writeString(pidFile, ProcessHandle.current().pid() + " " + port + " /not/the/recorded/node");

            var source = Map.of(
                "XUI_PORT", Integer.toString(port),
                "RSE_LIB_CACHE_DIR", cache.toString(),
                "RSE_LIB_XUI_DIR", "/cftlib/does/not/exist");

            assertThatThrownBy(() -> Xui.manageCases().start(source))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("already in use");
        }
    }

    @Test
    void refusesAPortHeldBySomethingElse(@TempDir Path cache) throws Exception {
        try (var listening = new ServerSocket(0)) {
            // No pid file, so the listener is not one of ours: a leftover container, most often.
            var source = Map.of(
                "XUI_PORT", Integer.toString(listening.getLocalPort()),
                "RSE_LIB_CACHE_DIR", cache.toString(),
                "RSE_LIB_XUI_DIR", "/cftlib/does/not/exist");

            assertThatThrownBy(() -> Xui.manageCases().start(source))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("already in use")
                .hasMessageContaining("RSE_LIB_XUI=off");
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
    void releasesTheBuildLockForTheNextBoot(@TempDir Path cache) throws Exception {
        assumeTrue(hostNodeVersion() != null, "no node on the path");
        var source = Map.of(
            "XUI_PORT", Integer.toString(freePort()),
            "RSE_LIB_XUI_NODE_RANGE", ">=0",
            "RSE_LIB_CACHE_DIR", cache.toString(),
            "RSE_LIB_XUI_DIR", "/cftlib/does/not/exist");

        // Each boot locks the clone it shares with every other project on the machine. A lock
        // or a channel left behind would deny the next boot that lock, rather than the same
        // failure this one had.
        for (var attempt : java.util.List.of("first", "second")) {
            assertThatThrownBy(() -> Xui.manageCases().start(source))
                .as("the %s boot", attempt)
                .hasMessageContaining("is not a directory");
        }
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
