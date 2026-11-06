package uk.gov.hmcts.rse.ccd.lib;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServiceBusConfigTest {

    @Test
    void mergesEachServicesEntitiesIntoTheEmulatorsOneNamespace(@TempDir Path dir) throws Exception {
        var civil = Files.writeString(dir.resolve("civil.json"), """
            {"Topics": [{"Name": "hmc-to-cft", "Subscriptions": [{"Name": "hmc-to-civil"}]}]}
            """);
        var hmc = Files.writeString(dir.resolve("hmc.json"), """
            {"Queues": [{"Name": "hmc-from-hmi"}],
             "Topics": [{"Name": "hmc-to-cft", "Subscriptions": [{"Name": "hmc-subs-to-cft"}]}]}
            """);
        var config = dir.resolve("compose/servicebus/Config.json");

        ServiceBusConfig.write(config, Map.of("RSE_LIB_FEATURES", "servicebus",
            "RSE_LIB_SERVICEBUS_CONFIG", civil + ", " + hmc));

        JsonObject namespace = JsonParser.parseString(Files.readString(config)).getAsJsonObject()
            .getAsJsonObject("UserConfig").getAsJsonArray("Namespaces").get(0).getAsJsonObject();
        assertThat(namespace.get("Name").getAsString()).isEqualTo("sbemulatorns");
        assertThat(namespace.getAsJsonArray("Queues")).hasSize(1);
        // A topic declared twice gets both declarations' subscriptions.
        var topics = namespace.getAsJsonArray("Topics");
        assertThat(topics).hasSize(1);
        assertThat(subscriptionNames(topics.get(0).getAsJsonObject()))
            .containsExactly("hmc-to-civil", "hmc-subs-to-cft");
    }

    @Test
    void addsTheEntitiesOfTheServicesAFeatureRuns(@TempDir Path dir) throws Exception {
        var civil = Files.writeString(dir.resolve("civil.json"), """
            {"Topics": [{"Name": "hmc-to-cft", "Subscriptions": [{"Name": "hmc-to-civil"}]}]}
            """);
        var config = dir.resolve("Config.json");

        ServiceBusConfig.write(config, Map.of("RSE_LIB_FEATURES", "servicebus,hmc",
            "RSE_LIB_SERVICEBUS_CONFIG", civil.toString()));

        var namespace = JsonParser.parseString(Files.readString(config)).getAsJsonObject()
            .getAsJsonObject("UserConfig").getAsJsonArray("Namespaces").get(0).getAsJsonObject();
        assertThat(namespace.getAsJsonArray("Queues").asList().stream()
            .map(q -> q.getAsJsonObject().get("Name").getAsString()))
            .containsExactly("hmc-to-hmi", "hmc-from-hmi");
        var topic = namespace.getAsJsonArray("Topics").get(0).getAsJsonObject();
        assertThat(topic.get("Name").getAsString()).isEqualTo("hmc-to-cft");
        assertThat(subscriptionNames(topic)).containsExactly("hmc-to-civil");
    }

    private static List<String> subscriptionNames(JsonObject topic) {
        return topic.getAsJsonArray("Subscriptions").asList().stream()
            .map(s -> s.getAsJsonObject().get("Name").getAsString())
            .toList();
    }

    @Test
    void writesAnEmptyNamespaceWhenNoServiceDeclaresEntities(@TempDir Path dir) throws Exception {
        var config = dir.resolve("Config.json");

        ServiceBusConfig.write(config, Map.of());

        var namespace = JsonParser.parseString(Files.readString(config)).getAsJsonObject()
            .getAsJsonObject("UserConfig").getAsJsonArray("Namespaces").get(0).getAsJsonObject();
        assertThat(namespace.getAsJsonArray("Queues")).isEmpty();
        assertThat(namespace.getAsJsonArray("Topics")).isEmpty();
    }

    @Test
    void subscribesToATopicNoServiceSubscribesTo(@TempDir Path dir) throws Exception {
        var config = dir.resolve("compose/servicebus/Config.json");

        // hmc's topic, with no service declaring a subscription to it.
        ServiceBusConfig.write(config, Map.of("RSE_LIB_FEATURES", "servicebus,hmc"));

        JsonObject namespace = JsonParser.parseString(Files.readString(config)).getAsJsonObject()
            .getAsJsonObject("UserConfig").getAsJsonArray("Namespaces").get(0).getAsJsonObject();
        assertThat(subscriptionNames(namespace.getAsJsonArray("Topics").get(0).getAsJsonObject()))
            .containsExactly("cftlib");
    }

    @Test
    void ignoresTheBuildsEntitiesWithoutTheServicebusFeature(@TempDir Path dir) throws Exception {
        var config = dir.resolve("compose/servicebus/Config.json");

        ServiceBusConfig.write(config, Map.of("RSE_LIB_SERVICEBUS_CONFIG", dir.resolve("missing.json").toString()));

        assertThat(Files.exists(config)).isTrue();
    }

    @Test
    void isOnlyEnabledByTheServicebusFeature() {
        assertThat(ServiceBusConfig.enabled(Map.of("RSE_LIB_FEATURES", "camunda,servicebus"))).isTrue();
        assertThat(ServiceBusConfig.enabled(Map.of("RSE_LIB_FEATURES", "camunda"))).isFalse();
        assertThat(ServiceBusConfig.enabled(Map.of())).isFalse();
    }
}
