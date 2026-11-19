package uk.gov.hmcts.rse.ccd.lib.hmc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;

/**
 * The Service Bus senders for HMC's queues and topic, one for each, reused for every message.
 *
 * <p>HMC builds a new sender, with its own AMQP connection, for every message it sends, and never closes it. Azure
 * Service Bus tolerates the leaked connections; the local Service Bus emulator stops opening send links after a few,
 * so hearing updates stop reaching services. cftlib replaces HMC's senders with ones that send through these.
 *
 * <p>Each HMC application has its own classloader, so its own senders.
 */
public final class ServiceBusSenders {

    private static final Map<String, ServiceBusSenderClient> SENDERS = new ConcurrentHashMap<>();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(ServiceBusSenders::closeAll, "cftlib-hmc-senders"));
    }

    private ServiceBusSenders() {
    }

    public static void sendToQueue(String connectionString, String queue, ServiceBusMessage message) {
        SENDERS.computeIfAbsent("queue " + queue + " " + connectionString, key -> new ServiceBusClientBuilder()
                .connectionString(connectionString)
                .sender()
                .queueName(queue)
                .buildClient())
            .sendMessage(message);
    }

    public static void sendToTopic(String connectionString, String topic, ServiceBusMessage message) {
        SENDERS.computeIfAbsent("topic " + topic + " " + connectionString, key -> new ServiceBusClientBuilder()
                .connectionString(connectionString)
                .sender()
                .topicName(topic)
                .buildClient())
            .sendMessage(message);
    }

    private static void closeAll() {
        SENDERS.values().forEach(sender -> {
            try {
                sender.close();
            } catch (RuntimeException e) {
                // Shutting down regardless.
            }
        });
    }
}
