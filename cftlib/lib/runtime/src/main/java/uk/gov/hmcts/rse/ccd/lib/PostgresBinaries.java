package uk.gov.hmcts.rse.ccd.lib;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Locale;
import java.util.zip.ZipFile;

import io.zonky.test.db.postgres.embedded.PgBinaryResolver;
import io.zonky.test.db.postgres.util.ArchUtils;

/**
 * Supplies zonky's postgres binaries by downloading the single jar this machine needs from maven
 * central and caching it under the user's home, so that cftlib does not have to depend on the
 * binaries for every platform.
 */
class PostgresBinaries implements PgBinaryResolver {

    static final PostgresBinaries INSTANCE = new PostgresBinaries();

    // The docker path runs postgres:16, so we track the latest 16.x binaries release.
    static final String VERSION = "16.15.0";

    private static final String REPOSITORY = "https://repo1.maven.org/maven2/io/zonky/test/postgres";

    private PostgresBinaries() {
    }

    @Override
    public InputStream getPgBinary(String system, String machineHardware) throws IOException {
        var platform = platform(system, machineHardware);
        var jar = download(platform);
        try {
            return openEntry(jar, platform.resource());
        } catch (IOException e) {
            Files.deleteIfExists(jar);
            throw new IOException(
                "The cached postgres binaries at " + jar + " could not be read and have been deleted. "
                    + "Re-run to download them again. The read failed with: " + e.getMessage(), e);
        }
    }

    static Platform platform(String system, String machineHardware) {
        return platform(system, machineHardware, Files.exists(Path.of("/etc/alpine-release")));
    }

    static Platform platform(String system, String machineHardware, boolean musl) {
        var os = system.toLowerCase(Locale.ROOT);
        var architecture = ArchUtils.normalize(machineHardware);
        var alpine = musl && "linux".equals(os);
        return new Platform(
            "embedded-postgres-binaries-" + target(os, architecture) + (alpine ? "-alpine" : ""),
            "postgres-" + os + "-" + architecture + (alpine ? "-alpine_linux" : "") + ".txz");
    }

    /**
     * The directory zonky unpacks the binaries into, kept out of the temp directory so that it
     * survives a reboot.
     */
    static Path extractionDirectory() {
        return home().resolve("postgres");
    }

    static Path cacheDirectory() {
        return home().resolve("postgres-binaries");
    }

    /**
     * Zonky makes its working directory world writable so that it can be shared between users.
     * Under the user's home that is a needless exposure of the binaries the JVM then executes.
     */
    static void restrictExtractionDirectory() {
        try {
            Files.setPosixFilePermissions(extractionDirectory(), PosixFilePermissions.fromString("rwxr-xr-x"));
        } catch (IOException | UnsupportedOperationException e) {
            // Permissions are a hardening detail, not something to fail a boot over.
        }
    }

    private static String target(String os, String architecture) {
        return switch (os + "/" + architecture) {
            case "linux/x86_64" -> "linux-amd64";
            case "linux/arm_64" -> "linux-arm64v8";
            case "darwin/x86_64" -> "darwin-amd64";
            case "darwin/arm_64" -> "darwin-arm64v8";
            case "windows/x86_64" -> "windows-amd64";
            default -> throw new IllegalStateException(
                "No postgres " + VERSION + " binaries are published for " + os + "/" + architecture
                    + ". Run the dependencies in docker with RSE_LIB_DEPENDENCY_MODE=docker.");
        };
    }

    private static Path home() {
        // Overridable so that a test run caches outside the engineer's own ~/.cftlib.
        var configured = System.getProperty("cftlib.cacheDir", System.getenv("RSE_LIB_CACHE_DIR"));
        return configured != null && !configured.isBlank()
            ? Path.of(configured.trim()).toAbsolutePath().normalize()
            : Path.of(System.getProperty("user.home"), ".cftlib");
    }

    private static Path download(Platform platform) throws IOException {
        var name = platform.artifact() + "-" + VERSION + ".jar";
        var cached = cacheDirectory().resolve(name);
        if (Files.isRegularFile(cached) && Files.size(cached) > 0) {
            return cached;
        }

        var url = REPOSITORY + "/" + platform.artifact() + "/" + VERSION + "/" + name;
        System.out.println("**** Cftlib downloading the postgres " + VERSION + " binaries from " + url);

        Files.createDirectories(cached.getParent());
        var partial = Files.createTempFile(cached.getParent(), name, ".part");
        try {
            copy(url, partial);
            Files.move(partial, cached, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IOException(
                "Could not download the postgres " + VERSION + " binaries from " + url
                    + ". Either download that file by hand to " + cached
                    + ", or run the dependencies in docker with RSE_LIB_DEPENDENCY_MODE=docker. "
                    + "The download failed with: " + e.getMessage(), e);
        } finally {
            Files.deleteIfExists(partial);
        }
        return cached;
    }

    private static void copy(String url, Path target) throws IOException {
        var connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(120_000);
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("the server responded " + connection.getResponseCode() + " "
                    + connection.getResponseMessage());
            }
            try (var in = connection.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static InputStream openEntry(Path jar, String resource) throws IOException {
        var zip = new ZipFile(jar.toFile());
        var entry = zip.getEntry(resource);
        if (entry == null) {
            zip.close();
            throw new IOException("it does not contain " + resource);
        }
        return new FilterInputStream(zip.getInputStream(entry)) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    zip.close();
                }
            }
        };
    }

    /**
     * A published binaries artifact and the name of the archive it contains.
     */
    record Platform(String artifact, String resource) {
    }
}
