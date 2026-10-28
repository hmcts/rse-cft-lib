package uk.gov.hmcts.rse.ccd.lib;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class XuiBuildMarkerTest {

    private static final String COMMIT = "4d344f6ff7815ecf507a9db38667be6a7cf8d375";
    private static final String OTHER_COMMIT = "0000000000000000000000000000000000000000";
    private static final String NODE = "v24.22.1";

    @Test
    void markerForTheSameCheckoutCommitTreeAndNodeIsUpToDate(@TempDir Path dir) throws Exception {
        var marker = dir.resolve("markers/manage-cases.built");
        var checkout = dir.resolve("manage-cases");
        var output = builtOutput(checkout);

        XuiBuildMarker.write(marker, checkout, COMMIT, "clean", NODE);

        assertThat(XuiBuildMarker.upToDate(marker, checkout, COMMIT, "clean", NODE, output)).isTrue();
    }

    @Test
    void anAbsentMarkerIsNotUpToDate(@TempDir Path dir) throws Exception {
        var checkout = dir.resolve("manage-cases");

        assertThat(XuiBuildMarker.upToDate(dir.resolve("manage-cases.built"), checkout, COMMIT, "clean",
            NODE, builtOutput(checkout))).isFalse();
    }

    @Test
    void anEditedCheckoutIsNotUpToDate(@TempDir Path dir) throws Exception {
        var marker = dir.resolve("manage-cases.built");
        var checkout = dir.resolve("cache/manage-cases");
        var output = builtOutput(checkout);

        XuiBuildMarker.write(marker, checkout, COMMIT, "clean", NODE);

        assertThat(XuiBuildMarker.upToDate(marker, checkout, COMMIT, "a1b2c3:42", NODE, output)).isFalse();
    }

    @Test
    void markerForAnotherCommitIsNotUpToDate(@TempDir Path dir) throws Exception {
        var marker = dir.resolve("manage-cases.built");
        var checkout = dir.resolve("manage-cases");
        var output = builtOutput(checkout);

        XuiBuildMarker.write(marker, checkout, COMMIT, "clean", NODE);

        assertThat(XuiBuildMarker.upToDate(marker, checkout, OTHER_COMMIT, "clean", NODE, output)).isFalse();
    }

    @Test
    void markerForAnotherCheckoutIsNotUpToDate(@TempDir Path dir) throws Exception {
        var marker = dir.resolve("manage-cases.built");
        var other = dir.resolve("my-own-clone");

        XuiBuildMarker.write(marker, dir.resolve("cache/manage-cases"), COMMIT, "clean", NODE);

        assertThat(XuiBuildMarker.upToDate(marker, other, COMMIT, "clean", NODE, builtOutput(other))).isFalse();
    }

    @Test
    void markerForAnotherNodeIsNotUpToDate(@TempDir Path dir) throws Exception {
        var marker = dir.resolve("manage-cases.built");
        var checkout = dir.resolve("manage-cases");
        var output = builtOutput(checkout);

        XuiBuildMarker.write(marker, checkout, COMMIT, "clean", NODE);

        // A build made by one node is not necessarily runnable by another.
        assertThat(XuiBuildMarker.upToDate(marker, checkout, COMMIT, "clean", "v25.0.0", output)).isFalse();
    }

    @Test
    void buildWhoseOutputHasGoneIsNotUpToDate(@TempDir Path dir) throws Exception {
        var marker = dir.resolve("manage-cases.built");
        var checkout = dir.resolve("manage-cases");
        var output = builtOutput(checkout);

        XuiBuildMarker.write(marker, checkout, COMMIT, "clean", NODE);
        // The output is gitignored, so a git clean removes it without touching the commit or
        // the working tree the marker records.
        Files.delete(output);

        assertThat(XuiBuildMarker.upToDate(marker, checkout, COMMIT, "clean", NODE, output)).isFalse();
    }

    @Test
    void markerInAnOlderFormatIsNotUpToDate(@TempDir Path dir) throws Exception {
        var marker = dir.resolve("manage-cases.built");
        var checkout = dir.resolve("manage-cases");
        var output = builtOutput(checkout);
        XuiBuildMarker.write(marker, checkout, COMMIT, "clean", NODE);

        Files.writeString(marker, Files.readString(marker).replace("format=" + XuiBuildMarker.FORMAT, "format=0"));

        assertThat(XuiBuildMarker.upToDate(marker, checkout, COMMIT, "clean", NODE, output)).isFalse();
    }

    @Test
    void theMarkerLivesOutsideTheCheckout(@TempDir Path dir) throws Exception {
        var checkout = Files.createDirectories(dir.resolve("manage-cases"));
        var marker = dir.resolve("manage-cases.built");

        XuiBuildMarker.write(marker, checkout, COMMIT, "clean", NODE);

        try (var contents = Files.list(checkout)) {
            assertThat(contents).isEmpty();
        }
    }

    /** The build output the marker vouches for, which has to exist for it to be reusable. */
    private Path builtOutput(Path checkout) throws Exception {
        var output = checkout.resolve("dist/server.bundle.js");
        Files.createDirectories(output.getParent());
        Files.writeString(output, "built");
        return output;
    }
}
