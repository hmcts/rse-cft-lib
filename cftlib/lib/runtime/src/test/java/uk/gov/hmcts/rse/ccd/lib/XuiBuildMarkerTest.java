package uk.gov.hmcts.rse.ccd.lib;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class XuiBuildMarkerTest {

    private static final String COMMIT = "4d344f6ff7815ecf507a9db38667be6a7cf8d375";

    @Test
    void anAbsentMarkerIsNotUpToDate(@TempDir Path tempDir) throws Exception {
        assertThat(XuiBuildMarker.upToDate(tempDir.resolve("manage-cases.built"), tempDir, COMMIT)).isFalse();
    }

    @Test
    void markerForTheSameCheckoutAndCommitIsUpToDate(@TempDir Path tempDir) throws Exception {
        var marker = tempDir.resolve("markers/manage-cases.built");
        var checkout = tempDir.resolve("manage-cases");

        XuiBuildMarker.write(marker, checkout, COMMIT);

        assertThat(XuiBuildMarker.upToDate(marker, checkout, COMMIT)).isTrue();
    }

    @Test
    void markerForAnotherCommitIsNotUpToDate(@TempDir Path tempDir) throws Exception {
        var marker = tempDir.resolve("manage-cases.built");
        var checkout = tempDir.resolve("manage-cases");

        XuiBuildMarker.write(marker, checkout, COMMIT);

        assertThat(XuiBuildMarker.upToDate(marker, checkout, "0000000000000000000000000000000000000000"))
            .isFalse();
    }

    @Test
    void markerForAnotherCheckoutIsNotUpToDate(@TempDir Path tempDir) throws Exception {
        var marker = tempDir.resolve("manage-cases.built");

        XuiBuildMarker.write(marker, tempDir.resolve("cache/manage-cases"), COMMIT);

        assertThat(XuiBuildMarker.upToDate(marker, tempDir.resolve("my-own-clone"), COMMIT)).isFalse();
    }

    @Test
    void markerInAnOlderFormatIsNotUpToDate(@TempDir Path tempDir) throws Exception {
        var marker = tempDir.resolve("manage-cases.built");
        var checkout = tempDir.resolve("manage-cases");
        XuiBuildMarker.write(marker, checkout, COMMIT);

        Files.writeString(marker, Files.readString(marker).replace("format=" + XuiBuildMarker.FORMAT, "format=0"));

        assertThat(XuiBuildMarker.upToDate(marker, checkout, COMMIT)).isFalse();
    }

    @Test
    void theMarkerLivesOutsideTheCheckout(@TempDir Path tempDir) throws Exception {
        var checkout = Files.createDirectories(tempDir.resolve("manage-cases"));
        var marker = tempDir.resolve("manage-cases.built");

        XuiBuildMarker.write(marker, checkout, COMMIT);

        try (var contents = Files.list(checkout)) {
            assertThat(contents).isEmpty();
        }
    }
}
