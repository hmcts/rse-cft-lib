package uk.gov.hmcts.rse.ccd.lib;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.SneakyThrows;

/**
 * The Azure Service Bus emulator's Config.json, which declares every queue, topic and subscription up front.
 *
 * <p>Services add their entities with RSE_LIB_SERVICEBUS_CONFIG, a comma separated list of JSON files in the
 * emulator's own format for a namespace: {@code {"Queues": [...], "Topics": [...]}}. Features add the entities of
 * the services they run, such as HMC's for hmc, ahead of those. A queue, topic or subscription declared twice keeps
 * its first declaration, but a topic gets the subscriptions of every declaration, so a service can subscribe to a
 * topic another declares.
 */
public class ServiceBusConfig {
    // The emulator supports a single namespace with this name.
    static final String NAMESPACE = "sbemulatorns";

    static boolean enabled(Map<String, String> env) {
        return Arrays.asList(env.getOrDefault("RSE_LIB_FEATURES", "").split(",")).contains("servicebus");
    }

    // The entities each feature's services use, as classpath resources.
    private static final Map<String, String> FEATURE_ENTITIES = Map.of("hmc", "/servicebus/hmc.json");

    @SneakyThrows
    static void write(Path file, Map<String, String> env) {
        var namespaces = new ArrayList<JsonElement>();
        var features = Arrays.asList(env.getOrDefault("RSE_LIB_FEATURES", "").split(","));
        for (var feature : FEATURE_ENTITIES.entrySet()) {
            if (features.contains(feature.getKey())) {
                try (var in = ServiceBusConfig.class.getResourceAsStream(feature.getValue())) {
                    namespaces.add(JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
                }
            }
        }
        for (var path : sources(env)) {
            namespaces.add(JsonParser.parseString(Files.readString(Path.of(path), StandardCharsets.UTF_8)));
        }
        Files.createDirectories(file.getParent());
        Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(config(namespaces)),
            StandardCharsets.UTF_8);
    }

    static List<String> sources(Map<String, String> env) {
        return Arrays.stream(env.getOrDefault("RSE_LIB_SERVICEBUS_CONFIG", "").split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }

    static JsonObject config(List<JsonElement> namespaces) {
        var namespace = new JsonObject();
        namespace.addProperty("Name", NAMESPACE);
        namespace.add("Queues", merge(namespaces, "Queues"));
        namespace.add("Topics", merge(namespaces, "Topics"));

        var all = new JsonArray();
        all.add(namespace);
        var logging = new JsonObject();
        logging.addProperty("Type", "File");
        var userConfig = new JsonObject();
        userConfig.add("Namespaces", all);
        userConfig.add("Logging", logging);
        var root = new JsonObject();
        root.add("UserConfig", userConfig);
        return root;
    }

    private static JsonArray merge(List<JsonElement> namespaces, String kind) {
        var byName = new LinkedHashMap<String, JsonObject>();
        for (var namespace : namespaces) {
            var entities = namespace.getAsJsonObject().getAsJsonArray(kind);
            if (entities == null) {
                continue;
            }
            for (var e : entities) {
                var entity = e.getAsJsonObject();
                var existing = byName.putIfAbsent(name(entity), entity.deepCopy());
                if (existing != null && entity.has("Subscriptions")) {
                    addSubscriptions(existing, entity.getAsJsonArray("Subscriptions"));
                }
            }
        }
        var result = new JsonArray();
        byName.values().forEach(result::add);
        return result;
    }

    private static void addSubscriptions(JsonObject topic, JsonArray subscriptions) {
        if (!topic.has("Subscriptions")) {
            topic.add("Subscriptions", new JsonArray());
        }
        var existing = topic.getAsJsonArray("Subscriptions");
        var names = new HashSet<String>();
        existing.forEach(s -> names.add(name(s.getAsJsonObject())));
        for (var s : subscriptions) {
            if (names.add(name(s.getAsJsonObject()))) {
                existing.add(s.deepCopy());
            }
        }
    }

    private static String name(JsonObject entity) {
        return entity.get("Name").getAsString();
    }
}
