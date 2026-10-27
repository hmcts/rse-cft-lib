package uk.gov.hmcts.rse.ccd.lib;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * The environment the two XUI apps run with, for both docker and host process mode.
 *
 * <p>Docker compose consumes this via the generated env_file that {@link ComposeRunner} writes,
 * and {@link Xui} passes it straight to the node process. The {@code ${VAR:-default}} semantics
 * of the compose file are preserved by {@link #value}: a variable that is unset or empty in the
 * interpolation source falls back to the default.
 */
final class XuiEnvironment {

    static final String XUI_ENV_PREFIX = "RSE_LIB_XUI_ENV_";

    private static final String DOCKER_JVM_HOST = "host.docker.internal";
    private static final String APPINSIGHTS_CONNECTION_STRING =
        "InstrumentationKey=dummy-key;IngestionEndpoint=https://uksouth-0.in.applicationinsights.azure.com/";

    /**
     * Where an XUI app runs relative to the JVM hosting the CFT services.
     */
    enum Network {
        /** In a container, reaching the JVM across the docker bridge. */
        DOCKER,
        /** On the host, reaching the JVM on localhost. */
        HOST
    }

    private XuiEnvironment() {
    }

    /**
     * The variables compose interpolates from, which are the JVM's environment overlaid with its
     * system properties. Auto-managed AAT secrets are set as system properties and must be visible
     * here for both modes to resolve them the same way.
     */
    static Map<String, String> interpolationSource() {
        return interpolationSource(System.getenv(), System.getProperties());
    }

    static Map<String, String> interpolationSource(Map<String, String> env, Properties properties) {
        var source = new LinkedHashMap<String, String>();
        if (env != null) {
            source.putAll(env);
        }
        if (properties != null) {
            for (var key : properties.stringPropertyNames()) {
                source.put(key, properties.getProperty(key));
            }
        }
        return source;
    }

    static Map<String, String> manageCases(Map<String, String> rawSource, Network network, int port) {
        var source = withLocalAuth(rawSource, network);
        var jvm = "http://" + jvmHost(source, network);
        var datastore = jvm + ":4452";
        var caseAssignment = jvm + ":4454";
        var roleAssignment = jvm + ":4096";
        var docAssembly = value(source, "XUI_EM_DOCASSEMBLY_API",
            "http://dg-docassembly-aat.service.core-compute-aat.internal");
        var waTaskApi = value(source, "SERVICES_WORK_ALLOCATION_TASK_API",
            "http://wa-task-management-api-aat.service.core-compute-aat.internal");
        var locationRefApi = "http://rd-location-ref-api-aat.service.core-compute-aat.internal";
        var judicialApi = "http://rd-judicial-api-aat.service.core-compute-aat.internal";

        var env = new LinkedHashMap<String, String>();
        env.put("PORT", Integer.toString(port));
        env.put("FEATURE_APP_INSIGHTS_ENABLED", "false");
        env.put("FEATURE_SECURE_COOKIE_ENABLED", "false");
        env.put("FEATURE_REDIS_ENABLED", "false");
        env.put("JURISDICTIONS", value(source, "XUI_JURISDICTIONS",
            "DIVORCE,PROBATE,SSCS,PRIVATELAW,PUBLICLAW,ADOPTION,CIVIL,EMPLOYMENT"));
        env.put("MICROSERVICE", "xui_webapp");
        env.put("PROTOCOL", "http");
        env.put("XUI_ENV", "local");
        env.put("DEBUG", "*:*");
        env.put("SERVICES_DOCUMENTS_API", value(source, "XUI_DOCUMENTS_API",
            "http://dm-store-aat.service.core-compute-aat.internal"));
        env.put("SERVICES_DOCUMENTS_API_V2", value(source, "XUI_DOCUMENTS_API_V2", jvm + ":4455"));
        env.put("SERVICES_PAYMENTS_URL", "http://payment-api-aat.service.core-compute-aat.internal");
        env.put("SERVICES_EM_ANNO_API", datastore);
        env.put("SERVICES_CCD_COMPONENT_API", datastore);
        env.put("SERVICES_CCD_DATA_STORE_API", datastore);
        env.put("SERVICES_CCD_CASE_ASSIGNMENT_API", caseAssignment);
        env.put("SERVICES_IDAM_API_URL", value(source, "XUI_IDAM_API_URL",
            "https://idam-api.aat.platform.hmcts.net"));
        env.put("SERVICES_IDAM_CLIENT_ID", "xuiwebapp");
        env.put("SERVICES_IDAM_LOGIN_URL", value(source, "XUI_IDAM_LOGIN_URL",
            "https://idam-web-public.aat.platform.hmcts.net"));
        env.put("SERVICES_IDAM_INDEX_URL", "/");
        env.put("SERVICES_IDAM_OAUTH_CALLBACK_URL", "/oauth2/callback");
        env.put("SERVICES_EM_DOCASSEMBLY_API", docAssembly);
        env.put("HEALTH_EM_DOCASSEMBLY_API", docAssembly + "/health");
        env.put("SERVICES_S2S", value(source, "XUI_S2S_URL",
            "http://rpe-service-auth-provider-aat.service.core-compute-aat.internal"));
        env.put("SYSTEM_USER_NAME", value(source, "XUI_SYSTEM_USER_NAME", "cwd_system@mailinator.com"));
        env.put("SYSTEM_USER_PASSWORD", value(source, "XUI_SYSTEM_USER_PASSWORD", "password"));
        env.put("REDISCLOUD_URL", "http://localhost:6780");
        env.put("HEALTH_CCD_COMPONENT_API", datastore + "/health");
        env.put("HEALTH_CCD_DATA_API", datastore + "/health");
        env.put("SERVICES_PRD_API", value(source, "RD_PROFESSIONAL_API_URL",
            "http://rd-professional-api-aat.service.core-compute-aat.internal"));
        env.put("APPINSIGHTS_INSTRUMENTATIONKEY", "TESTVAR");
        env.put("APPINSIGHTS_CONNECTION_STRING", APPINSIGHTS_CONNECTION_STRING);
        env.put("IDAM_SECRET", value(source, "XUI_OAUTH_SECRET", "AAAAAAAAAAAAAAA"));
        env.put("S2S_SECRET", value(source, "XUI_SERVICE_KEY", "AAAAAAAAAAAAAAAA"));
        env.put("SESSION_SECRET", value(source, "XUI_SESSION_SECRET", "secretSauce"));
        env.put("LAUNCH_DARKLY_CLIENT_ID", value(source, "XUI_LD_ID", "5de6610b23ce5408280f2268"));
        env.put("SERVICES_ROLE_ASSIGNMENT_API", roleAssignment);
        env.put("HEALTH_ROLE_ASSIGNMENT_API", roleAssignment + "/health");
        env.put("SERVICES_PRD_COMMONDATA_API", value(source, "SERVICES_PRD_COMMONDATA_API",
            "http://rd-commondata-api-aat.service.core-compute-aat.internal"));
        env.put("SERVICES_PRD_LOCATION_API", value(source, "SERVICES_PRD_LOCATION_API", locationRefApi));
        env.put("SERVICES_PRD_JUDICIAL_API", value(source, "SERVICES_PRD_JUDICIAL_API", judicialApi));
        env.put("SERVICES_CASE_CASEWORKER_API", value(source, "SERVICES_CASE_CASEWORKER_API",
            "http://rd-caseworker-ref-api-aat.service.core-compute-aat.internal"));
        env.put("SERVICES_CASE_JUDICIAL_API", value(source, "SERVICES_CASE_JUDICIAL_API", judicialApi));
        env.put("SERVICES_CASE_JUDICALWORKER_API", value(source, "SERVICES_CASE_JUDICALWORKER_API",
            "http://rd-judicial-ref-api-aat.service.core-compute-aat.internal"));
        env.put("SERVICES_LOCATION_API", value(source, "SERVICES_LOCATION_API", locationRefApi));
        env.put("SERVICES_WORK_ALLOCATION_TASK_API", waTaskApi);
        env.put("HEALTH_WORK_ALLOCATION_TASK_API", waTaskApi + "/health");
        env.put("SERVICES_LOCATION_REF_API_URL", value(source, "SERVICES_LOCATION_REF_API_URL", locationRefApi));
        env.put("FEATURE_WORKALLOCATION_ENABLED", value(source, "FEATURE_WORKALLOCATION_ENABLED", "false"));
        env.put("WA_SUPPORTED_JURISDICTIONS", value(source, "WA_SUPPORTED_JURISDICTIONS",
            "IA,CIVIL,PRIVATELAW,PUBLICLAW,EMPLOYMENT,ST_CIC"));
        env.put("SERVICES_HMC_HEARINGS_COMPONENT_API", value(source, "XUI_SERVICES_HMC_HEARINGS_COMPONENT_API",
            "http://hmc-cft-hearing-service-aat.service.core-compute-aat.internal"));
        env.put("SERVICES_HEARINGS_COMPONENT_API_PRIVATELAW",
            value(source, "XUI_SERVICES_HEARINGS_COMPONENT_API_PRIVATELAW",
                "http://fis-hmc-api-aat.service.core-compute-aat.internal"));
        env.put("SERVICES_JUDICIAL_BOOKING_API_PATH", value(source, "SERVICES_JUDICIAL_BOOKING_API_PATH",
            "http://am-judicial-booking-service-aat.service.core-compute-aat.internal"));

        env.putAll(overrides(rawSource));
        return env;
    }

    static Map<String, String> manageOrg(Map<String, String> rawSource, Network network, int port) {
        var source = withLocalAuth(rawSource, network);
        var jvm = "http://" + jvmHost(source, network);
        var caseAssignment = jvm + ":4454";

        var env = new LinkedHashMap<String, String>();
        env.put("PORT", Integer.toString(port));
        env.put("DEBUG", "*:*");
        env.put("IDAM_API_SERVICE", value(source, "XUI_IDAM_API_URL",
            "https://idam-api.aat.platform.hmcts.net"));
        env.put("IDAM_WEB_SERVICE", value(source, "XUI_IDAM_LOGIN_URL",
            "https://idam-web-public.aat.platform.hmcts.net"));
        env.put("RD_PROFESSIONAL_API_SERVICE", value(source, "RD_PROFESSIONAL_API_URL",
            "http://rd-professional-api-aat.service.core-compute-aat.internal"));
        env.put("S2S_SERVICE", value(source, "XUI_S2S_URL",
            "http://rpe-service-auth-provider-aat.service.core-compute-aat.internal"));
        env.put("FEE_AND_PAY_API", "http://payment-api-aat.service.core-compute-aat.internal");
        env.put("SERVICES_CCD_DATA_STORE_API", jvm + ":4452");
        env.put("SYSTEM_USER_NAME", value(source, "XUI_SYSTEM_USER_NAME", "cwd_system@mailinator.com"));
        env.put("SYSTEM_USER_PASSWORD", value(source, "XUI_SYSTEM_USER_PASSWORD", "password"));
        env.put("ACA_SERVICE_API_BASEURL", caseAssignment);
        env.put("SERVICES_MCA_PROXY_API", caseAssignment);
        env.put("APPINSIGHTS_INSTRUMENTATIONKEY", "AAAAAAAAAAAAAAAC");
        env.put("APPINSIGHTS_CONNECTION_STRING", APPINSIGHTS_CONNECTION_STRING);
        env.put("IDAM_SECRET", value(source, "XUI_MANAGE_ORG_OAUTH_SECRET", "AAAAAAAAAAAAAAA"));
        env.put("S2S_SECRET", value(source, "XUI_SERVICE_KEY", "AAAAAAAAAAAAAAAA"));
        env.put("LAUNCH_DARKLY_CLIENT_ID", value(source, "XUI_LD_ID", "5de6610b23ce5408280f2268"));
        env.put("APPINSIGHTS_INSTRUMENTATIONKEY_MO", "AAAAAAAAAAAAAAAC");
        env.put("GOOGLE_ANALYTICS_KEY", "AAAAAAAAAAAAAAAC");
        env.put("REDISCLOUD_URL", "AAAAAAAAAAAAAAAC");
        env.put("NODE_CONFIG_ENV", "local");
        env.put("IDAM_CLIENT", "xuimowebapp");
        env.put("MICROSERVICE", "xui_webapp");
        env.put("OAUTH_CALLBACK_URL", "/oauth2/callback");
        env.put("PROTOCOL", "http");
        env.put("SESSION_SECRET", value(source, "XUI_SESSION_SECRET", "secretSauce"));
        env.put("CASE_TYPES", value(source, "XUI_MANAGE_ORGANISATIONS_CASETYPES",
            "NFD,Caveat,GrantOfRepresentation"));
        env.put("FEATURE_APP_INSIGHTS_ENABLED", "false");
        env.put("FEATURE_PROXY_ENABLED", "false");
        env.put("FEATURE_SECURE_COOKIE_ENABLED", "false");
        env.put("FEATURE_TERMS_AND_CONDITIONS_ENABLED", "false");
        env.put("FEATURE_HELMET_ENABLED", "false");
        env.put("FEATURE_REDIS_ENABLED", "false");
        env.put("FEATURE_OIDC_ENABLED", "false");
        env.put("LOGGING", "debug");

        // Declared with no value in compose, which passes the variable through only when it is set.
        passThrough(env, rawSource, "LOGIN_ROLE_MATCHER");
        passThrough(env, rawSource, "SERVICES_TERMS_AND_CONDITIONS_API_SERVICE");
        passThrough(env, rawSource, "MANAGE_CASE_LINK");
        passThrough(env, rawSource, "MANAGE_ORG_LINK");

        return env;
    }

    /**
     * The host XUI uses to reach the JVM. In process mode there is no container network, so the
     * {@code JVM_HOST} indirection collapses to localhost.
     */
    static String jvmHost(Map<String, String> source, Network network) {
        if (network == Network.HOST) {
            return "localhost";
        }
        return value(source, "JVM_HOST", DOCKER_JVM_HOST);
    }

    /**
     * The URL XUI redirects the browser to for login, which must be reachable from the browser
     * rather than from within the docker network.
     */
    static String xuiIdamLoginUrl(Map<String, String> source) {
        return value(source, "XUI_IDAM_LOGIN_URL",
            value(source, "IDAM_SIMULATOR_BASE_URL_OUTSIDE_NETWORK", "http://localhost:5062"));
    }

    /**
     * Arbitrary extra variables for manage cases, supplied as {@code RSE_LIB_XUI_ENV_<NAME>}
     * environment variables or system properties.
     */
    static Map<String, String> overrides(Map<String, String> source) {
        var output = new LinkedHashMap<String, String>();
        if (source == null) {
            return output;
        }
        for (var entry : source.entrySet()) {
            var key = entry.getKey();
            if (key == null || !key.startsWith(XUI_ENV_PREFIX)) {
                continue;
            }
            var stripped = key.substring(XUI_ENV_PREFIX.length());
            var value = entry.getValue();
            if (!stripped.isBlank() && value != null && !value.isBlank()) {
                output.put(stripped, value);
            }
        }
        return output;
    }

    /**
     * Points the XUI service URLs at the locally running CFT services when cftlib is managing auth.
     */
    private static Map<String, String> withLocalAuth(Map<String, String> source, Network network) {
        if (!"localAuth".equals(source.get("RSE_LIB_AUTH-MODE"))) {
            return source;
        }
        var host = "http://" + jvmHost(source, network);
        var runtime = host + ":5062";

        var resolved = new LinkedHashMap<>(source);
        resolved.put("XUI_S2S_URL", host + ":" + value(source, "RSE_LIB_S2S_PORT", "8489"));
        resolved.put("XUI_IDAM_API_URL", runtime);
        resolved.put("XUI_IDAM_LOGIN_URL", xuiIdamLoginUrl(source));
        resolved.put("SERVICES_WORK_ALLOCATION_TASK_API", host + ":8087");
        // Placeholders so the XUI health checks pass without these services running.
        resolved.put("XUI_EM_DOCASSEMBLY_API", value(source, "XUI_EM_DOCASSEMBLY_API", runtime));
        resolved.put("XUI_DOCUMENTS_API", value(source, "XUI_DOCUMENTS_API", runtime));
        resolved.put("XUI_DOCUMENTS_API_V2", value(source, "XUI_DOCUMENTS_API_V2", runtime));
        return resolved;
    }

    private static void passThrough(Map<String, String> env, Map<String, String> source, String key) {
        var value = source.get(key);
        if (value != null) {
            env.put(key, value);
        }
    }

    private static String value(Map<String, String> source, String key, String defaultValue) {
        var value = source.get(key);
        return value == null || value.isEmpty() ? defaultValue : value;
    }
}
