package uk.gov.hmcts.rse.ccd.lib;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class XuiEnvironmentTest {

    /**
     * The manage cases environment as the compose file declared it, with no variables set.
     * Any change to a default has to be a deliberate change to this map.
     */
    private static final Map<String, String> MANAGE_CASES_DEFAULTS = Map.ofEntries(
        Map.entry("PORT", "3000"),
        Map.entry("FEATURE_APP_INSIGHTS_ENABLED", "false"),
        Map.entry("FEATURE_SECURE_COOKIE_ENABLED", "false"),
        Map.entry("FEATURE_REDIS_ENABLED", "false"),
        Map.entry("JURISDICTIONS", "DIVORCE,PROBATE,SSCS,PRIVATELAW,PUBLICLAW,ADOPTION,CIVIL,EMPLOYMENT"),
        Map.entry("MICROSERVICE", "xui_webapp"),
        Map.entry("PROTOCOL", "http"),
        Map.entry("XUI_ENV", "local"),
        Map.entry("DEBUG", "*:*"),
        Map.entry("SERVICES_DOCUMENTS_API", "http://dm-store-aat.service.core-compute-aat.internal"),
        Map.entry("SERVICES_DOCUMENTS_API_V2", "http://host.docker.internal:4455"),
        Map.entry("SERVICES_PAYMENTS_URL", "http://payment-api-aat.service.core-compute-aat.internal"),
        Map.entry("SERVICES_EM_ANNO_API", "http://host.docker.internal:4452"),
        Map.entry("SERVICES_CCD_COMPONENT_API", "http://host.docker.internal:4452"),
        Map.entry("SERVICES_CCD_DATA_STORE_API", "http://host.docker.internal:4452"),
        Map.entry("SERVICES_CCD_CASE_ASSIGNMENT_API", "http://host.docker.internal:4454"),
        Map.entry("SERVICES_IDAM_API_URL", "https://idam-api.aat.platform.hmcts.net"),
        Map.entry("SERVICES_IDAM_CLIENT_ID", "xuiwebapp"),
        Map.entry("SERVICES_IDAM_LOGIN_URL", "https://idam-web-public.aat.platform.hmcts.net"),
        Map.entry("SERVICES_IDAM_INDEX_URL", "/"),
        Map.entry("SERVICES_IDAM_OAUTH_CALLBACK_URL", "/oauth2/callback"),
        Map.entry("SERVICES_EM_DOCASSEMBLY_API", "http://dg-docassembly-aat.service.core-compute-aat.internal"),
        Map.entry("HEALTH_EM_DOCASSEMBLY_API", "http://dg-docassembly-aat.service.core-compute-aat.internal/health"),
        Map.entry("SERVICES_S2S", "http://rpe-service-auth-provider-aat.service.core-compute-aat.internal"),
        Map.entry("SYSTEM_USER_NAME", "cwd_system@mailinator.com"),
        Map.entry("SYSTEM_USER_PASSWORD", "password"),
        Map.entry("REDISCLOUD_URL", "http://localhost:6780"),
        Map.entry("HEALTH_CCD_COMPONENT_API", "http://host.docker.internal:4452/health"),
        Map.entry("HEALTH_CCD_DATA_API", "http://host.docker.internal:4452/health"),
        Map.entry("SERVICES_PRD_API", "http://rd-professional-api-aat.service.core-compute-aat.internal"),
        Map.entry("APPINSIGHTS_INSTRUMENTATIONKEY", "TESTVAR"),
        Map.entry("APPINSIGHTS_CONNECTION_STRING",
            "InstrumentationKey=dummy-key;IngestionEndpoint=https://uksouth-0.in.applicationinsights.azure.com/"),
        Map.entry("IDAM_SECRET", "AAAAAAAAAAAAAAA"),
        Map.entry("S2S_SECRET", "AAAAAAAAAAAAAAAA"),
        Map.entry("SESSION_SECRET", "secretSauce"),
        Map.entry("LAUNCH_DARKLY_CLIENT_ID", "5de6610b23ce5408280f2268"),
        Map.entry("SERVICES_ROLE_ASSIGNMENT_API", "http://host.docker.internal:4096"),
        Map.entry("HEALTH_ROLE_ASSIGNMENT_API", "http://host.docker.internal:4096/health"),
        Map.entry("SERVICES_PRD_COMMONDATA_API", "http://rd-commondata-api-aat.service.core-compute-aat.internal"),
        Map.entry("SERVICES_PRD_LOCATION_API", "http://rd-location-ref-api-aat.service.core-compute-aat.internal"),
        Map.entry("SERVICES_PRD_JUDICIAL_API", "http://rd-judicial-api-aat.service.core-compute-aat.internal"),
        Map.entry("SERVICES_CASE_CASEWORKER_API",
            "http://rd-caseworker-ref-api-aat.service.core-compute-aat.internal"),
        Map.entry("SERVICES_CASE_JUDICIAL_API", "http://rd-judicial-api-aat.service.core-compute-aat.internal"),
        Map.entry("SERVICES_CASE_JUDICALWORKER_API",
            "http://rd-judicial-ref-api-aat.service.core-compute-aat.internal"),
        Map.entry("SERVICES_LOCATION_API", "http://rd-location-ref-api-aat.service.core-compute-aat.internal"),
        Map.entry("SERVICES_WORK_ALLOCATION_TASK_API",
            "http://wa-task-management-api-aat.service.core-compute-aat.internal"),
        Map.entry("HEALTH_WORK_ALLOCATION_TASK_API",
            "http://wa-task-management-api-aat.service.core-compute-aat.internal/health"),
        Map.entry("SERVICES_LOCATION_REF_API_URL",
            "http://rd-location-ref-api-aat.service.core-compute-aat.internal"),
        Map.entry("FEATURE_WORKALLOCATION_ENABLED", "false"),
        Map.entry("WA_SUPPORTED_JURISDICTIONS", "IA,CIVIL,PRIVATELAW,PUBLICLAW,EMPLOYMENT,ST_CIC"),
        Map.entry("SERVICES_HMC_HEARINGS_COMPONENT_API",
            "http://hmc-cft-hearing-service-aat.service.core-compute-aat.internal"),
        Map.entry("SERVICES_HEARINGS_COMPONENT_API_PRIVATELAW",
            "http://fis-hmc-api-aat.service.core-compute-aat.internal"),
        Map.entry("SERVICES_JUDICIAL_BOOKING_API_PATH",
            "http://am-judicial-booking-service-aat.service.core-compute-aat.internal"));

    /**
     * The manage organisations environment as the compose file declared it, with no variables set.
     */
    private static final Map<String, String> MANAGE_ORG_DEFAULTS = Map.ofEntries(
        Map.entry("PORT", "3001"),
        Map.entry("DEBUG", "*:*"),
        Map.entry("IDAM_API_SERVICE", "https://idam-api.aat.platform.hmcts.net"),
        Map.entry("IDAM_WEB_SERVICE", "https://idam-web-public.aat.platform.hmcts.net"),
        Map.entry("RD_PROFESSIONAL_API_SERVICE", "http://rd-professional-api-aat.service.core-compute-aat.internal"),
        Map.entry("S2S_SERVICE", "http://rpe-service-auth-provider-aat.service.core-compute-aat.internal"),
        Map.entry("FEE_AND_PAY_API", "http://payment-api-aat.service.core-compute-aat.internal"),
        Map.entry("SERVICES_CCD_DATA_STORE_API", "http://host.docker.internal:4452"),
        Map.entry("SYSTEM_USER_NAME", "cwd_system@mailinator.com"),
        Map.entry("SYSTEM_USER_PASSWORD", "password"),
        Map.entry("ACA_SERVICE_API_BASEURL", "http://host.docker.internal:4454"),
        Map.entry("SERVICES_MCA_PROXY_API", "http://host.docker.internal:4454"),
        Map.entry("APPINSIGHTS_INSTRUMENTATIONKEY", "AAAAAAAAAAAAAAAC"),
        Map.entry("APPINSIGHTS_CONNECTION_STRING",
            "InstrumentationKey=dummy-key;IngestionEndpoint=https://uksouth-0.in.applicationinsights.azure.com/"),
        Map.entry("IDAM_SECRET", "AAAAAAAAAAAAAAA"),
        Map.entry("S2S_SECRET", "AAAAAAAAAAAAAAAA"),
        Map.entry("LAUNCH_DARKLY_CLIENT_ID", "5de6610b23ce5408280f2268"),
        Map.entry("APPINSIGHTS_INSTRUMENTATIONKEY_MO", "AAAAAAAAAAAAAAAC"),
        Map.entry("GOOGLE_ANALYTICS_KEY", "AAAAAAAAAAAAAAAC"),
        Map.entry("REDISCLOUD_URL", "AAAAAAAAAAAAAAAC"),
        Map.entry("NODE_CONFIG_ENV", "local"),
        Map.entry("IDAM_CLIENT", "xuimowebapp"),
        Map.entry("MICROSERVICE", "xui_webapp"),
        Map.entry("OAUTH_CALLBACK_URL", "/oauth2/callback"),
        Map.entry("PROTOCOL", "http"),
        Map.entry("SESSION_SECRET", "secretSauce"),
        Map.entry("CASE_TYPES", "NFD,Caveat,GrantOfRepresentation"),
        Map.entry("FEATURE_APP_INSIGHTS_ENABLED", "false"),
        Map.entry("FEATURE_PROXY_ENABLED", "false"),
        Map.entry("FEATURE_SECURE_COOKIE_ENABLED", "false"),
        Map.entry("FEATURE_TERMS_AND_CONDITIONS_ENABLED", "false"),
        Map.entry("FEATURE_HELMET_ENABLED", "false"),
        Map.entry("FEATURE_REDIS_ENABLED", "false"),
        Map.entry("FEATURE_OIDC_ENABLED", "false"),
        Map.entry("LOGGING", "debug"));

    @Test
    void manageCasesDefaultsMatchTheComposeFile() {
        var env = XuiEnvironment.manageCases(Map.of(), XuiEnvironment.Network.DOCKER, 3000);

        assertThat(env).containsExactlyInAnyOrderEntriesOf(MANAGE_CASES_DEFAULTS);
    }

    @Test
    void manageOrgDefaultsMatchTheComposeFile() {
        var env = XuiEnvironment.manageOrg(Map.of(), XuiEnvironment.Network.DOCKER, 3001);

        assertThat(env).containsExactlyInAnyOrderEntriesOf(MANAGE_ORG_DEFAULTS);
    }

    @Test
    void manageCasesOnTheHostReachesTheJvmOnLocalhost() {
        var env = XuiEnvironment.manageCases(Map.of("JVM_HOST", "10.0.0.1"),
            XuiEnvironment.Network.HOST, 3000);

        assertThat(env)
            .containsEntry("SERVICES_CCD_DATA_STORE_API", "http://localhost:4452")
            .containsEntry("HEALTH_CCD_DATA_API", "http://localhost:4452/health")
            .containsEntry("SERVICES_CCD_CASE_ASSIGNMENT_API", "http://localhost:4454")
            .containsEntry("SERVICES_ROLE_ASSIGNMENT_API", "http://localhost:4096")
            .containsEntry("SERVICES_DOCUMENTS_API_V2", "http://localhost:4455");
        assertThat(env.values()).noneMatch(value -> value.contains("host.docker.internal"));
    }

    @Test
    void manageOrgOnTheHostReachesTheJvmOnLocalhost() {
        var env = XuiEnvironment.manageOrg(Map.of("JVM_HOST", "10.0.0.1"), XuiEnvironment.Network.HOST, 3001);

        assertThat(env)
            .containsEntry("SERVICES_CCD_DATA_STORE_API", "http://localhost:4452")
            .containsEntry("ACA_SERVICE_API_BASEURL", "http://localhost:4454")
            .containsEntry("SERVICES_MCA_PROXY_API", "http://localhost:4454");
        assertThat(env.values()).noneMatch(value -> value.contains("host.docker.internal"));
    }

    @Test
    void manageCasesInDockerHonoursJvmHost() {
        var env = XuiEnvironment.manageCases(Map.of("JVM_HOST", "10.0.0.1"),
            XuiEnvironment.Network.DOCKER, 3000);

        assertThat(env).containsEntry("SERVICES_CCD_DATA_STORE_API", "http://10.0.0.1:4452");
    }

    @Test
    void theOnlyDifferenceBetweenTheTwoNetworksIsTheJvmHost() {
        var docker = XuiEnvironment.manageCases(Map.of(), XuiEnvironment.Network.DOCKER, 3000);
        var host = XuiEnvironment.manageCases(Map.of(), XuiEnvironment.Network.HOST, 3000);

        var collapsed = new LinkedHashMap<String, String>();
        docker.forEach((key, value) -> collapsed.put(key, value.replace("host.docker.internal", "localhost")));

        assertThat(host).isEqualTo(collapsed);
    }

    @Test
    void portIsTheHostPortOnTheHostAndTheContainerPortInDocker() {
        var source = Map.of("XUI_PORT", "3456", "XUI_MO_PORT", "3457");

        assertThat(XuiApp.MANAGE_CASES.port(source, XuiEnvironment.Network.HOST)).isEqualTo(3456);
        assertThat(XuiApp.MANAGE_CASES.port(source, XuiEnvironment.Network.DOCKER)).isEqualTo(3000);
        assertThat(XuiApp.MANAGE_ORG.port(source, XuiEnvironment.Network.HOST)).isEqualTo(3457);
        assertThat(XuiApp.MANAGE_ORG.port(source, XuiEnvironment.Network.DOCKER)).isEqualTo(3001);
        assertThat(XuiApp.MANAGE_CASES.port(Map.of(), XuiEnvironment.Network.HOST)).isEqualTo(3000);
        assertThat(XuiApp.MANAGE_ORG.port(Map.of(), XuiEnvironment.Network.HOST)).isEqualTo(3001);
    }

    @Test
    void emptyVariablesFallBackToTheDefaultAsComposeDoes() {
        var env = XuiEnvironment.manageCases(Map.of("XUI_JURISDICTIONS", ""),
            XuiEnvironment.Network.DOCKER, 3000);

        assertThat(env).containsEntry("JURISDICTIONS",
            MANAGE_CASES_DEFAULTS.get("JURISDICTIONS"));
    }

    @Test
    void setVariablesOverrideTheDefaults() {
        var source = Map.of(
            "XUI_JURISDICTIONS", "PCS",
            "XUI_DOCUMENTS_API", "http://documents",
            "XUI_EM_DOCASSEMBLY_API", "http://docassembly",
            "XUI_OAUTH_SECRET", "oauth",
            "XUI_SERVICE_KEY", "s2s",
            "XUI_SESSION_SECRET", "session",
            "XUI_LD_ID", "ld",
            "XUI_SYSTEM_USER_NAME", "user",
            "XUI_SYSTEM_USER_PASSWORD", "pass",
            "SERVICES_WORK_ALLOCATION_TASK_API", "http://wa");

        var env = XuiEnvironment.manageCases(source, XuiEnvironment.Network.DOCKER, 3000);

        assertThat(env)
            .containsEntry("JURISDICTIONS", "PCS")
            .containsEntry("SERVICES_DOCUMENTS_API", "http://documents")
            .containsEntry("SERVICES_EM_DOCASSEMBLY_API", "http://docassembly")
            .containsEntry("HEALTH_EM_DOCASSEMBLY_API", "http://docassembly/health")
            .containsEntry("IDAM_SECRET", "oauth")
            .containsEntry("S2S_SECRET", "s2s")
            .containsEntry("SESSION_SECRET", "session")
            .containsEntry("LAUNCH_DARKLY_CLIENT_ID", "ld")
            .containsEntry("SYSTEM_USER_NAME", "user")
            .containsEntry("SYSTEM_USER_PASSWORD", "pass")
            .containsEntry("SERVICES_WORK_ALLOCATION_TASK_API", "http://wa")
            .containsEntry("HEALTH_WORK_ALLOCATION_TASK_API", "http://wa/health");
    }

    @Test
    void manageOrgPassesThroughUnsetVariablesOnlyWhenTheyAreSet() {
        var unset = XuiEnvironment.manageOrg(Map.of(), XuiEnvironment.Network.DOCKER, 3001);

        assertThat(unset)
            .doesNotContainKey("LOGIN_ROLE_MATCHER")
            .doesNotContainKey("SERVICES_TERMS_AND_CONDITIONS_API_SERVICE")
            .doesNotContainKey("MANAGE_CASE_LINK")
            .doesNotContainKey("MANAGE_ORG_LINK");

        var set = XuiEnvironment.manageOrg(Map.of("MANAGE_CASE_LINK", "http://localhost:3000"),
            XuiEnvironment.Network.DOCKER, 3001);

        assertThat(set).containsEntry("MANAGE_CASE_LINK", "http://localhost:3000");
    }

    @Test
    void manageOrgUsesItsOwnIdamSecret() {
        var source = Map.of("XUI_MANAGE_ORG_OAUTH_SECRET", "mo", "XUI_OAUTH_SECRET", "mc");

        assertThat(XuiEnvironment.manageOrg(source, XuiEnvironment.Network.DOCKER, 3001))
            .containsEntry("IDAM_SECRET", "mo");
        assertThat(XuiEnvironment.manageCases(source, XuiEnvironment.Network.DOCKER, 3000))
            .containsEntry("IDAM_SECRET", "mc");
    }

    @Test
    void localAuthPointsManageCasesAtTheLocalServices() {
        var source = Map.of("RSE_LIB_AUTH-MODE", "localAuth");

        var env = XuiEnvironment.manageCases(source, XuiEnvironment.Network.HOST, 3000);

        assertThat(env)
            .containsEntry("SERVICES_S2S", "http://localhost:8489")
            .containsEntry("SERVICES_IDAM_API_URL", "http://localhost:5062")
            .containsEntry("SERVICES_IDAM_LOGIN_URL", "http://localhost:5062")
            .containsEntry("SERVICES_WORK_ALLOCATION_TASK_API", "http://localhost:8087")
            .containsEntry("HEALTH_WORK_ALLOCATION_TASK_API", "http://localhost:8087/health")
            .containsEntry("SERVICES_EM_DOCASSEMBLY_API", "http://localhost:5062")
            .containsEntry("SERVICES_DOCUMENTS_API", "http://localhost:5062")
            .containsEntry("SERVICES_DOCUMENTS_API_V2", "http://localhost:5062");
    }

    @Test
    void localAuthInDockerUsesTheDockerHostForServicesAndLocalhostForTheBrowser() {
        var source = Map.of("RSE_LIB_AUTH-MODE", "localAuth", "RSE_LIB_S2S_PORT", "9999");

        var env = XuiEnvironment.manageCases(source, XuiEnvironment.Network.DOCKER, 3000);

        assertThat(env)
            .containsEntry("SERVICES_S2S", "http://host.docker.internal:9999")
            .containsEntry("SERVICES_IDAM_API_URL", "http://host.docker.internal:5062")
            .containsEntry("SERVICES_IDAM_LOGIN_URL", "http://localhost:5062");
    }

    @Test
    void localAuthPointsManageOrgAtTheLocalServices() {
        var source = Map.of("RSE_LIB_AUTH-MODE", "localAuth");

        var env = XuiEnvironment.manageOrg(source, XuiEnvironment.Network.HOST, 3001);

        assertThat(env)
            .containsEntry("IDAM_API_SERVICE", "http://localhost:5062")
            .containsEntry("IDAM_WEB_SERVICE", "http://localhost:5062")
            .containsEntry("S2S_SERVICE", "http://localhost:8489");
    }

    @Test
    void localAuthLeavesDocumentOverridesAlone() {
        var source = Map.of(
            "RSE_LIB_AUTH-MODE", "localAuth",
            "XUI_DOCUMENTS_API", "http://documents",
            "XUI_DOCUMENTS_API_V2", "http://documents/v2",
            "XUI_EM_DOCASSEMBLY_API", "http://docassembly");

        var env = XuiEnvironment.manageCases(source, XuiEnvironment.Network.HOST, 3000);

        assertThat(env)
            .containsEntry("SERVICES_DOCUMENTS_API", "http://documents")
            .containsEntry("SERVICES_DOCUMENTS_API_V2", "http://documents/v2")
            .containsEntry("SERVICES_EM_DOCASSEMBLY_API", "http://docassembly");
    }

    @Test
    void buildsOverridesFromEnvAndProperties() {
        Map<String, String> env = new HashMap<>();
        env.put("RSE_LIB_XUI_ENV_DECENTRALISED_EVENT_BASE_URLS", "{\"E2E\":\"https://example.com\"}");
        env.put("RSE_LIB_XUI_ENV_EMPTY", "");

        Properties properties = new Properties();
        properties.setProperty("RSE_LIB_XUI_ENV_FOO", "bar");
        properties.setProperty("RSE_LIB_XUI_ENV_DECENTRALISED_EVENT_BASE_URLS", "{\"E2E\":\"https://override\"}");

        var result = XuiEnvironment.overrides(XuiEnvironment.interpolationSource(env, properties));

        assertThat(result)
            .containsEntry("FOO", "bar")
            .containsEntry("DECENTRALISED_EVENT_BASE_URLS", "{\"E2E\":\"https://override\"}")
            .doesNotContainKey("EMPTY");
    }

    @Test
    void overridesAreAppliedToManageCasesInBothModes() {
        var source = Map.of("RSE_LIB_XUI_ENV_DECENTRALISED_CASE_TYPE_CONFIG", "{}");

        assertThat(XuiEnvironment.manageCases(source, XuiEnvironment.Network.DOCKER, 3000))
            .containsEntry("DECENTRALISED_CASE_TYPE_CONFIG", "{}");
        assertThat(XuiEnvironment.manageCases(source, XuiEnvironment.Network.HOST, 3000))
            .containsEntry("DECENTRALISED_CASE_TYPE_CONFIG", "{}");
    }

    @Test
    void systemPropertiesOverrideEnvironmentVariablesInTheInterpolationSource() {
        Properties properties = new Properties();
        properties.setProperty("XUI_SESSION_SECRET", "fromProperty");

        var source = XuiEnvironment.interpolationSource(Map.of("XUI_SESSION_SECRET", "fromEnv"), properties);

        assertThat(XuiEnvironment.manageCases(source, XuiEnvironment.Network.DOCKER, 3000))
            .containsEntry("SESSION_SECRET", "fromProperty");
    }

    @Test
    void theComposeEnvFileCarriesExactlyTheProcessEnvironment(@TempDir Path tempDir) throws Exception {
        var source = Map.of(
            "RSE_LIB_AUTH-MODE", "localAuth",
            "RSE_LIB_XUI_ENV_DECENTRALISED_EVENT_BASE_URLS", "{\"E2E\":\"https://example.com\"}",
            "XUI_SESSION_SECRET", "has$dollar");
        var expected = XuiEnvironment.manageCases(source, XuiEnvironment.Network.DOCKER, 3000);
        var file = tempDir.resolve("xui.env");

        ComposeRunner.writeEnvFile(file, expected);

        assertThat(parseEnvFile(file)).containsExactlyInAnyOrderEntriesOf(expected);
    }

    @Test
    void xuiIdamLoginUrlDefaultsToLocalhost() {
        assertThat(XuiEnvironment.xuiIdamLoginUrl(Map.of())).isEqualTo("http://localhost:5062");
    }

    @Test
    void xuiIdamLoginUrlFollowsSimulatorOutsideNetworkUrl() {
        var env = Map.of("IDAM_SIMULATOR_BASE_URL_OUTSIDE_NETWORK", "https://idam.example.com");

        assertThat(XuiEnvironment.xuiIdamLoginUrl(env)).isEqualTo("https://idam.example.com");
    }

    @Test
    void xuiIdamLoginUrlCanBeOverridden() {
        var env = Map.of(
            "IDAM_SIMULATOR_BASE_URL_OUTSIDE_NETWORK", "https://idam.example.com",
            "XUI_IDAM_LOGIN_URL", "https://login.example.com"
        );

        assertThat(XuiEnvironment.xuiIdamLoginUrl(env)).isEqualTo("https://login.example.com");
    }

    private static Map<String, String> parseEnvFile(Path file) throws Exception {
        var parsed = new LinkedHashMap<String, String>();
        for (var line : Files.readAllLines(file)) {
            if (line.isBlank()) {
                continue;
            }
            var split = line.indexOf('=');
            parsed.put(line.substring(0, split), line.substring(split + 1).replace("$$", "$"));
        }
        return parsed;
    }
}
