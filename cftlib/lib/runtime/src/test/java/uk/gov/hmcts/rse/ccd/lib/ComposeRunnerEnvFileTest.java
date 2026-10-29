package uk.gov.hmcts.rse.ccd.lib;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ComposeRunnerEnvFileTest {

    @Test
    void writesEnvFile(@TempDir Path tempDir) throws Exception {
        Path envFile = tempDir.resolve("xui.env");
        Map<String, String> values = Map.of(
            "DECENTRALISED_EVENT_BASE_URLS", "{\"E2E\":\"https://example.com\"}",
            "FOO", "abc$BAR"
        );

        ComposeRunner.writeEnvFile(envFile, values);

        var contents = Files.readString(envFile);
        assertThat(contents).contains("DECENTRALISED_EVENT_BASE_URLS={\"E2E\":\"https://example.com\"}");
        assertThat(contents).contains("FOO=abc$$BAR");
    }

    @Test
    void writesAnEmptyFileWhenThereIsNothingToWrite(@TempDir Path tempDir) throws Exception {
        Path envFile = tempDir.resolve("xui.env");

        ComposeRunner.writeEnvFile(envFile, Map.of());

        assertThat(Files.readString(envFile)).isEmpty();
    }

    @Test
    void writesAnEnvFileForEachXuiService(@TempDir Path tempDir) throws Exception {
        ComposeRunner.writeXuiEnvFiles(tempDir);

        assertThat(Files.readString(tempDir.resolve("xui.env")))
            .contains("MICROSERVICE=xui_webapp")
            .contains("SERVICES_IDAM_CLIENT_ID=xuiwebapp")
            .contains("PORT=3000");
        assertThat(Files.readString(tempDir.resolve("xui-mo.env")))
            .contains("IDAM_CLIENT=xuimowebapp")
            .contains("PORT=3001");
    }
}
