package uk.gov.hmcts.rse.ccd.lib;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
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
 * emulator's own format for a namespace: {@code {"Queues": [...], "Topics": [...]}}. An entity declared twice keeps
 * its first declaration.
 */
public class ServiceBusConfig {
    // The emulator supports a single namespace with this name.
    static final String NAMESPACE = "sbemulatorns";

    static boolean enabled(Map<String, String> env) {
        return Arrays.asList(env.getOrDefault("RSE_LIB_FEATURES", "").split(",")).contains("servicebus");
    }

    @SneakyThrows
    static void write(Path file, Map<String, String> env) {
        var namespaces = new ArrayList<JsonElement>();
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
        var byName = new LinkedHashMap<String, JsonElement>();
        for (var namespace : namespaces) {
            var entities = namespace.getAsJsonObject().getAsJsonArray(kind);
            if (entities != null) {
                entities.forEach(e -> byName.putIfAbsent(e.getAsJsonObject().get("Name").getAsString(), e));
            }
        }
        var result = new JsonArray();
        byName.values().forEach(result::add);
        return result;
    }
}
