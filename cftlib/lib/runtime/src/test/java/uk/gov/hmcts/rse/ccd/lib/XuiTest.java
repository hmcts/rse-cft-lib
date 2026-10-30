package uk.gov.hmcts.rse.ccd.lib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class XuiTest {

    @Test
    void stopsTheInstanceAKilledBootLeftBehind(@TempDir Path cache) throws Exception {
        assumeTrue(hostNodeVersion() != null, "no node on the path");
        var port = freePort();
        // Holding the port, as the instance it stands in for does: stopping it has to free it.
        var stale = listener(port);
        try {
            // No boot holds the claim on this port, so the instance belongs to one that was
            // killed and is this boot's to stop. It is not taken over: what it was built from
            // cannot be told from what this boot wants without doing the fetch and the build
            // that taking it over exists to skip.
            record(cache, port, stale);

            var source = Map.of(
                "XUI_PORT", Integer.toString(port),
                "RSE_LIB_XUI_NODE_RANGE", ">=0",
                "RSE_LIB_CACHE_DIR", cache.toString(),
                "RSE_LIB_XUI_DIR", "/cftlib/does/not/exist");

            // Goes on to build and launch, rather than serving what it found.
            assertThatThrownBy(() -> Xui.manageCases().start(source))
                .hasMessageContaining("is not a directory");
            assertThat(stale.waitFor(10, TimeUnit.SECONDS))
                .as("the instance the killed boot left was stopped")
                .isTrue();
        } finally {
            stale.destroyForcibly();
        }
    }

    @Test
    void refusesAPortAnotherLiveBootHasClaimed(@TempDir Path cache) throws Exception {
        var port = freePort();
        var running = new ProcessBuilder("sleep", "600").start();
        // The claim another boot took on this port, which it holds for as long as it runs. Taken
        // before either boot binds, so a boot that is still building does not leave the port
        // looking free to the next one.
        try (var channel = FileChannel.open(pidFile(cache, port), StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, StandardOpenOption.READ);
             var claim = channel.lock()) {
            record(cache, port, running);

            var source = Map.of(
                "XUI_PORT", Integer.toString(port),
                "RSE_LIB_CACHE_DIR", cache.toString(),
                "RSE_LIB_XUI_DIR", "/cftlib/does/not/exist");

            assertThatThrownBy(() -> Xui.manageCases().start(source))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("belongs to the xui-manage-cases of another cftlib")
                .hasMessageContaining("XUI_PORT");
            assertThat(running.isAlive()).as("the other boot's instance is untouched").isTrue();
        } finally {
            running.destroyForcibly();
        }
    }

    @Test
    void refusesAPortWhoseRecordedPidIsNowSomethingElse(@TempDir Path cache) throws Exception {
        try (var listening = new ServerSocket(0)) {
            var port = listening.getLocalPort();
            // A live pid, but not the process that was recorded: after a reboot the pid belongs
            // to whatever claimed it next, and the start time is what tells them apart. Stopping
            // one of those would kill an unrelated process, an editor's node language server as
            // readily as anything.
            Files.writeString(pidFile(cache, port), String.join("\n",
                Long.toString(ProcessHandle.current().pid()),
                Instant.parse("2020-01-01T00:00:00Z").toString()));

            var source = Map.of(
                "XUI_PORT", Integer.toString(port),
                "RSE_LIB_CACHE_DIR", cache.toString(),
                "RSE_LIB_XUI_DIR", "/cftlib/does/not/exist");

            assertThatThrownBy(() -> Xui.manageCases().start(source))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("already in use");
            assertThat(ProcessHandle.current().isAlive()).isTrue();
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

    /** Writes what a boot records of the instance it leaves running, a field to a line. */
    private static void record(Path cache, int port, Process node) throws IOException {
        Files.writeString(pidFile(cache, port), String.join("\n",
            Long.toString(node.pid()),
            node.info().startInstant().orElseThrow().toString()));
    }

    /** A node process holding a port, standing in for an instance a killed boot left running. */
    private static Process listener(int port) throws Exception {
        var process = new ProcessBuilder("node", "-e",
            "require('net').createServer().listen(" + port + "); setInterval(() => {}, 1000);")
            .start();
        for (var waited = 0; waited < 200 && !Ports.isListening(port); waited++) {
            Thread.sleep(50);
        }
        assertThat(Ports.isListening(port)).as("the stand-in took port %s", port).isTrue();
        return process;
    }

    private static Path pidFile(Path cache, int port) throws IOException {
        return Files.createDirectories(cache.resolve("xui")).resolve("manage-cases-" + port + ".pid");
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
