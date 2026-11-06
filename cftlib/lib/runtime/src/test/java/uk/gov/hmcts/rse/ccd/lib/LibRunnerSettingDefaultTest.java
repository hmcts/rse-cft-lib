package uk.gov.hmcts.rse.ccd.lib;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;

class LibRunnerSettingDefaultTest {

    @ParameterizedTest
    @ValueSource(strings = {"repository/ccd-data-store-api/DEV-SNAPSHOT",
        "caches/modules-2/files-2.1/com.github.hmcts.rse-cft-lib/ccd-data-store-api/0.19.2446/hash"})
    void readsTheDefaultFromTheServicesOwnJar(String layout, @TempDir Path dir) throws Exception {
        var jar = Files.createDirectories(dir.resolve(layout))
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

    @Test
    void readsTheDefaultFromLocalProjectResources(@TempDir Path dir) throws Exception {
        var resources = Files.createDirectories(dir.resolve("service/build/resources/main"));
        Files.writeString(resources.resolve("application.yml"),
            "services: ${DATA_STORE_S2S_AUTHORISED_SERVICES:ccd_gw,ccd_data}\n");
        var manifest = Files.writeString(dir.resolve("ccd-data-store-api"),
            "uk.gov.hmcts.ccd.CoreCaseDataApplication\n" + resources + "\n");

        assertThat(LibRunner.settingDefault(List.of(manifest.toString()), "ccd-data-store-api",
            "DATA_STORE_S2S_AUTHORISED_SERVICES")).isEqualTo("ccd_gw,ccd_data");
    }

}