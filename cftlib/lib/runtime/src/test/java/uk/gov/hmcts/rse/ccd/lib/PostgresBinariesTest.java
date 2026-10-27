package uk.gov.hmcts.rse.ccd.lib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PostgresBinariesTest {

    @ParameterizedTest
    @CsvSource({
        "Linux,amd64,false,embedded-postgres-binaries-linux-amd64,postgres-linux-x86_64.txz",
        "Linux,x86_64,false,embedded-postgres-binaries-linux-amd64,postgres-linux-x86_64.txz",
        "Linux,aarch64,false,embedded-postgres-binaries-linux-arm64v8,postgres-linux-arm_64.txz",
        "Linux,amd64,true,embedded-postgres-binaries-linux-amd64-alpine,postgres-linux-x86_64-alpine_linux.txz",
        "Linux,aarch64,true,embedded-postgres-binaries-linux-arm64v8-alpine,postgres-linux-arm_64-alpine_linux.txz",
        "Darwin,x86_64,false,embedded-postgres-binaries-darwin-amd64,postgres-darwin-x86_64.txz",
        "Darwin,aarch64,false,embedded-postgres-binaries-darwin-arm64v8,postgres-darwin-arm_64.txz",
        "Darwin,aarch64,true,embedded-postgres-binaries-darwin-arm64v8,postgres-darwin-arm_64.txz",
        "Windows,amd64,false,embedded-postgres-binaries-windows-amd64,postgres-windows-x86_64.txz",
    })
    void mapsEachPlatformToItsPublishedArtifact(
        String system, String hardware, boolean musl, String artifact, String resource) {

        var platform = PostgresBinaries.platform(system, hardware, musl);

        assertThat(platform.artifact()).isEqualTo(artifact);
        assertThat(platform.resource()).isEqualTo(resource);
    }

    @Test
    void refusesAPlatformWithNoPublishedBinaries() {
        assertThatThrownBy(() -> PostgresBinaries.platform("Linux", "s390x", false))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("linux/s390_64")
            .hasMessageContaining("RSE_LIB_DEPENDENCY_MODE=docker");
    }

    @Test
    void downloadsAndCachesTheBinariesForThisMachine() throws IOException {
        var platform = PostgresBinaries.platform(system(), System.getProperty("os.arch"));

        try (var binary = PostgresBinaries.INSTANCE.getPgBinary(system(), System.getProperty("os.arch"))) {
            assertThat(binary.readNBytes(6)).isEqualTo(new byte[] {
                (byte) 0xfd, '7', 'z', 'X', 'Z', 0
            });
        }

        var cached = PostgresBinaries.cacheDirectory()
            .resolve(platform.artifact() + "-" + PostgresBinaries.VERSION + ".jar");
        assertThat(cached).isRegularFile();
        try (var jar = new ZipFile(cached.toFile())) {
            assertThat(jar.getEntry(platform.resource())).isNotNull();
        }
    }

    private static String system() {
        var name = System.getProperty("os.name");
        if (name.startsWith("Mac")) {
            return "Darwin";
        }
        if (name.startsWith("Windows")) {
            return "Windows";
        }
        return "Linux";
    }
}
