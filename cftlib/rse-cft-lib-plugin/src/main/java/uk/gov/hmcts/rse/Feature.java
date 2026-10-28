package uk.gov.hmcts.rse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gradle.api.InvalidUserDataException;

/**
 * Optional parts of the stack, which a build turns on per task with {@code features 'camunda'} or, for one run,
 * with RSE_LIB_FEATURES=camunda,servicebus.
 */
public enum Feature {
    // HMCTS's Camunda engine, camunda-bpm, in-process.
    camunda(List.of(), List.of()),
    // Azure Service Bus emulator in docker. It is a .NET application, so cannot run in-process.
    servicebus(List.of("servicebus"), List.of()),
    // Work allocation: wa-case-event-handler, wa-workflow-api and wa-task-monitor in-process, on Camunda.
    wa(List.of(), List.of(camunda)),
    // Hearings: hmc-cft-hearing-service and both HMI adapters in-process, on the Service Bus emulator, with a
    // stand-in for HMI.
    hmc(List.of(), List.of(servicebus));

    // The docker compose profiles the feature starts.
    public final List<String> composeProfiles;
    // The features it needs.
    public final List<Feature> requires;

    Feature(List<String> composeProfiles, List<Feature> requires) {
        this.composeProfiles = composeProfiles;
        this.requires = requires;
    }

    /**
     * The features named, with the features they need.
     */
    public static Set<Feature> resolve(Iterable<String> names) {
        var result = new LinkedHashSet<Feature>();
        for (var name : names) {
            var trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                add(result, parse(trimmed));
            }
        }
        return result;
    }

    public static Set<Feature> resolve(Iterable<String> configured, Map<String, ?> environment) {
        var names = new ArrayList<String>();
        configured.forEach(names::add);
        var requested = environment.get("RSE_LIB_FEATURES");
        if (requested != null) {
            names.addAll(Arrays.asList(requested.toString().split(",")));
        }
        return resolve(names);
    }

    private static void add(Set<Feature> result, Feature feature) {
        if (result.add(feature)) {
            feature.requires.forEach(f -> add(result, f));
        }
    }

    private static Feature parse(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new InvalidUserDataException("Unknown cftlib feature '" + name + "'. Known features: "
                + Arrays.toString(values()));
        }
    }
}
