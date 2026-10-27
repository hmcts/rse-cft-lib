package uk.gov.hmcts.rse.ccd.lib;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Records which checkout and commit cftlib last installed and built, so a boot can skip the
 * minutes long yarn install and Angular build.
 *
 * <p>Kept outside the checkout so that pointing cftlib at an engineer's own clone leaves no
 * untracked files in it. Bump {@link #FORMAT} when the install or build commands change, which
 * invalidates every existing marker.
 */
final class XuiBuildMarker {

    static final String FORMAT = "1";

    private XuiBuildMarker() {
    }

    static boolean upToDate(Path marker, Path checkout, String commit, String workingTree) throws IOException {
        if (!Files.exists(marker)) {
            return false;
        }
        var recorded = read(marker);
        return FORMAT.equals(recorded.getProperty("format"))
            && checkout.toString().equals(recorded.getProperty("checkout"))
            && commit.equals(recorded.getProperty("commit"))
            && workingTree.equals(recorded.getProperty("workingTree"));
    }

    static void write(Path marker, Path checkout, String commit, String workingTree) throws IOException {
        var properties = new Properties();
        properties.setProperty("format", FORMAT);
        properties.setProperty("checkout", checkout.toString());
        properties.setProperty("commit", commit);
        properties.setProperty("workingTree", workingTree);
        Files.createDirectories(marker.getParent());
        try (var writer = Files.newBufferedWriter(marker, StandardCharsets.UTF_8)) {
            properties.store(writer, "Written by cftlib; delete to force an XUI rebuild");
        }
    }

    private static Properties read(Path marker) throws IOException {
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(marker, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }
}
