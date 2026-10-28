package uk.gov.hmcts.rse.ccd.lib;

import java.util.Arrays;
import java.util.Locale;

/**
 * Resolves which implementation provides each cftlib dependency.
 *
 * <p>The overall mode is set by the Gradle task via RSE_LIB_DEPENDENCY_MODE and supplies the
 * default for each individual dependency, which can then be overridden independently.
 */
public final class CftlibConfig {

    public enum DependencyMode {
        DOCKER,
        PROCESS
    }

    public enum DbMode {
        DOCKER,
        EMBEDDED,
        EXTERNAL
    }

    public enum EsMode {
        DOCKER,
        STUB,
        PROCESS,
        EXTERNAL
    }

    public enum XuiMode {
        DOCKER,
        PROCESS,
        OFF
    }

    private CftlibConfig() {
    }

    public static DependencyMode dependencyMode() {
        return parse(DependencyMode.class, "RSE_LIB_DEPENDENCY_MODE", DependencyMode.DOCKER);
    }

    private static boolean processMode() {
        return dependencyMode() == DependencyMode.PROCESS;
    }

    public static DbMode dbMode() {
        return parse(DbMode.class, "RSE_LIB_DB", processMode() ? DbMode.EMBEDDED : DbMode.DOCKER);
    }

    public static EsMode esMode() {
        return parse(EsMode.class, "RSE_LIB_ES", processMode() ? EsMode.STUB : EsMode.DOCKER);
    }

    public static XuiMode xuiMode() {
        return parse(XuiMode.class, "RSE_LIB_XUI", processMode() ? XuiMode.PROCESS : XuiMode.DOCKER);
    }

    public static XuiMode xuiManageOrgMode() {
        return parse(XuiMode.class, "RSE_LIB_XUI_MO", processMode() ? XuiMode.OFF : XuiMode.DOCKER);
    }

    public static boolean localAuth() {
        return "localAuth".equals(System.getenv("RSE_LIB_AUTH-MODE"));
    }

    public static String dbHost() {
        return ControlPlane.getEnvVar("RSE_LIB_DB_HOST", "localhost");
    }

    public static int dbPort() {
        return Integer.parseInt(ControlPlane.getEnvVar("RSE_LIB_DB_PORT", 6432));
    }

    public static String dbUsername() {
        return ControlPlane.getEnvVar("RSE_LIB_DB_USERNAME", "postgres");
    }

    public static String dbPassword() {
        return ControlPlane.getEnvVar("RSE_LIB_DB_PASSWORD", "postgres");
    }

    /**
     * The base url of elasticsearch, as used by both the CFT services and the readiness probe.
     */
    public static String esUrl() {
        var configured = ControlPlane.getEnvVar("SEARCH_ELASTIC_HOSTS", "http://localhost:9200");
        // The data store accepts a comma separated list and tolerates a trailing slash; callers
        // here append paths to a single host, so take the first and drop the slash.
        var first = configured.split(",")[0].trim();
        while (first.endsWith("/")) {
            first = first.substring(0, first.length() - 1);
        }
        return first;
    }

    private static <T extends Enum<T>> T parse(Class<T> type, String var, T defaultValue) {
        var value = System.getenv(var);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                var + "=" + value + " is not valid. Expected one of "
                    + Arrays.toString(type.getEnumConstants()), e);
        }
    }
}
