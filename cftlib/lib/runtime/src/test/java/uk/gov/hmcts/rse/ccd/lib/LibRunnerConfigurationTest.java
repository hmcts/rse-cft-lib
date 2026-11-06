package uk.gov.hmcts.rse.ccd.lib;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class LibRunnerConfigurationTest {
    private static final List<String> SETTINGS = List.of("DM_STORE_BASE_URL", "CCD_DOCUMENT_URL_PATTERN",
        "IDAM_DATA_STORE_SYSTEM_USER_USERNAME", "IDAM_DATA_STORE_SYSTEM_USER_PASSWORD",
        "DATA_STORE_S2S_AUTHORISED_SERVICES", "ROLE_ASSIGNMENT_S2S_AUTHORISED_SERVICES");

    @Test
    void noFeaturesLeavesExistingLocalServiceDefaultsAlone() throws Exception {
        var output = configure("", Map.of(), List.of());
        for (var setting : SETTINGS) {
            assertThat(output).contains(setting + "=<unset>");
        }
    }

    @Test
    void localServicesSuppliesOptInDefaults() throws Exception {
        var output = configure("localServices", Map.of(), List.of());
        assertThat(output).contains("DM_STORE_BASE_URL=http://localhost:${RSE_LIB_S2S_PORT:8489}/dm-store",
            "IDAM_DATA_STORE_SYSTEM_USER_USERNAME=data-store-system-user@cftlib.local");
    }

    @Test
    void pcsLocalDocumentsKeepsItsExistingStoreAndPattern() throws Exception {
        var output = configure("", Map.of("CCD_DOCUMENT_URL_PATTERN",
            "http://localhost:4506/documents/[A-Za-z0-9-]+.*", "DM_STORE_APP_URL", "http://localhost:4506"), List.of());
        // An unset base URL preserves CDAM's own 4506 default; the build's pattern stays in the environment.
        assertThat(output).contains("DM_STORE_BASE_URL=<unset>", "CCD_DOCUMENT_URL_PATTERN=<unset>");
    }

    @Test
    void optInDefaultsDoNotReplaceExplicitJvmOrEnvironmentSettings() throws Exception {
        var output = configure("localServices", Map.of("CCD_DOCUMENT_URL_PATTERN", "consumer-pattern"),
            List.of("-DDM_STORE_BASE_URL=http://localhost:4506",
                "-DIDAM_DATA_STORE_SYSTEM_USER_USERNAME=consumer-user",
                "-DIDAM_DATA_STORE_SYSTEM_USER_PASSWORD=consumer-password"));
        assertThat(output).contains("DM_STORE_BASE_URL=http://localhost:4506", "CCD_DOCUMENT_URL_PATTERN=<unset>",
            "IDAM_DATA_STORE_SYSTEM_USER_USERNAME=consumer-user",
            "IDAM_DATA_STORE_SYSTEM_USER_PASSWORD=consumer-password");
    }

    @Test
    void addsFeatureCallersToJvmAllowListWithoutReplacingItWithTheEnvironment() throws Exception {
        var output = configure("wa,camunda,localServices",
            Map.of("DATA_STORE_S2S_AUTHORISED_SERVICES", "environment-caller"),
            List.of("-DDATA_STORE_S2S_AUTHORISED_SERVICES= consumer-caller , ccd_data "));
        assertThat(output)
            .contains("DATA_STORE_S2S_AUTHORISED_SERVICES=consumer-caller,ccd_data,wa_task_management_api,")
            .doesNotContain("environment-caller");
    }

    private String configure(String features, Map<String, String> environment, List<String> jvmArgs) throws Exception {
        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.addAll(jvmArgs);
        command.addAll(List.of("-cp", location(LibRunner.class) + File.pathSeparator + location(getClass()),
            Probe.class.getName()));
        var process = new ProcessBuilder(command).redirectErrorStream(true);
        SETTINGS.forEach(process.environment()::remove);
        process.environment().put("RSE_LIB_AUTH-MODE", "localAuth");
        process.environment().put("RSE_LIB_FEATURES", features);
        process.environment().putAll(environment);
        var child = process.start();
        try {
            assertThat(child.waitFor(30, TimeUnit.SECONDS)).isTrue();
            var output = new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(child.exitValue()).withFailMessage(output).isZero();
            return output;
        } finally {
            child.destroyForcibly();
        }
    }

    private String location(Class<?> type) throws Exception {
        return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
    }

    public static class Probe {
        public static void main(String[] args) {
            LibRunner.setConfigProperties(List.of());
            for (var setting : SETTINGS) {
                System.out.println(setting + "=" + System.getProperty(setting, "<unset>"));
            }
        }
    }
}
