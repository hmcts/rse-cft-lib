package uk.gov.hmcts.rse.ccd.lib;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The local JSON import payload shared by the runtime and definition store. */
public record JsonDefinitionImport(
        String directory,
        String template,
        Map<String, String> substitutions,
        List<String> excludedFilenamePatterns
) {
    public static final String PREFIX = "cftlib-json:";

    public JsonDefinitionImport {
        Objects.requireNonNull(directory, "directory");
        substitutions = Map.copyOf(substitutions);
        excludedFilenamePatterns = List.copyOf(excludedFilenamePatterns);
    }
}
