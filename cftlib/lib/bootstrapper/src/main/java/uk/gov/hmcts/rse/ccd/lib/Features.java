package uk.gov.hmcts.rse.ccd.lib;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The optional features this run has turned on, which the Gradle plugin lists in RSE_LIB_FEATURES with the
 * features they need already added.
 */
public final class Features {

    private Features() {
    }

    public static boolean enabled(String feature) {
        return enabled(System.getenv(), feature);
    }

    public static boolean enabled(Map<String, String> env, String feature) {
        return enabled(env).contains(feature);
    }

    public static Set<String> enabled(Map<String, String> env) {
        return Arrays.stream(env.getOrDefault("RSE_LIB_FEATURES", "").split(","))
            .map(String::trim)
            .filter(name -> !name.isEmpty())
            .collect(Collectors.toSet());
    }
}
