package uk.gov.hmcts.rse.ccd.lib;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ComposeRunnerEnvFileTest {

    @Test
    void buildsOverridesFromEnvAndProperties() {
        Map<String, String> env = new HashMap<>();
        env.put("RSE_LIB_XUI_ENV_DECENTRALISED_EVENT_BASE_URLS", "{\"E2E\":\"https://example.com\"}");
        env.put("RSE_LIB_XUI_ENV_EMPTY", "");

        Properties properties = new Properties();
        properties.setProperty("RSE_LIB_XUI_ENV_FOO", "bar");
        properties.setProperty("RSE_LIB_XUI_ENV_DECENTRALISED_EVENT_BASE_URLS", "{\"E2E\":\"https://override\"}");

        var result = ComposeRunner.buildPrefixedEnvOverrides(env, properties, ComposeRunner.XUI_ENV_PREFIX);

        assertThat(result)
            .containsEntry("FOO", "bar")
            .containsEntry("DECENTRALISED_EVENT_BASE_URLS", "{\"E2E\":\"https://override\"}")
            .doesNotContainKey("EMPTY");
    }

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
    void xuiIdamLoginUrlDefaultsToLocalhost() {
        assertThat(ComposeRunner.xuiIdamLoginUrl(Map.of())).isEqualTo("http://localhost:5062");
    }

    @Test
    void xuiIdamLoginUrlFollowsSimulatorOutsideNetworkUrl() {
        var env = Map.of("IDAM_SIMULATOR_BASE_URL_OUTSIDE_NETWORK", "https://idam.example.com");

        assertThat(ComposeRunner.xuiIdamLoginUrl(env)).isEqualTo("https://idam.example.com");
    }

    @Test
    void xuiIdamLoginUrlCanBeOverridden() {
        var env = Map.of(
            "IDAM_SIMULATOR_BASE_URL_OUTSIDE_NETWORK", "https://idam.example.com",
            "XUI_IDAM_LOGIN_URL", "https://login.example.com"
        );

        assertThat(ComposeRunner.xuiIdamLoginUrl(env)).isEqualTo("https://login.example.com");
    }
}
