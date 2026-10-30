package uk.gov.hmcts.rse.ccd.lib;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LibRunnerSettingDefaultTest {

    @Test
    void readsTheDefaultFromTheServicesOwnJar(@TempDir Path dir) throws Exception {
        var jar = Files.createDirectories(dir.resolve("repository/ccd-data-store-api/DEV-SNAPSHOT"))
            .resolve("ccd-data-store-api-DEV-SNAPSHOT.jar");
        try (var zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            zip.putNextEntry(new ZipEntry("application.properties"));
            zip.write("idam.s2s-authorised.services=${DATA_STORE_S2S_AUTHORISED_SERVICES:ccd_gw,ccd_data}\n"
                .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        var manifest = Files.writeString(dir.resolve("ccd-data-store-api"),
            "uk.gov.hmcts.ccd.CoreCaseDataApplication\n" + jar + "\n");

        assertThat(LibRunner.settingDefault(List.of(manifest.toString()), "ccd-data-store-api",
            "DATA_STORE_S2S_AUTHORISED_SERVICES")).isEqualTo("ccd_gw,ccd_data");
        assertThat(LibRunner.settingDefault(List.of(manifest.toString()), "ccd-data-store-api", "OTHER")).isNull();
        assertThat(LibRunner.settingDefault(List.of(), "ccd-data-store-api", "OTHER")).isNull();
    }
}
