package uk.gov.hmcts.libconsumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.models.DeadLetterOptions;
import com.azure.messaging.servicebus.models.ServiceBusReceiveMode;
import com.azure.messaging.servicebus.models.SubQueue;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import uk.gov.hmcts.rse.ccd.lib.test.CftlibTest;

/**
 * The Service Bus emulator cftlib runs with the servicebus feature, holding the entities in src/cftlib/servicebus.json.
 * Covers what CFT services rely on: sessions, dead-lettering and SQL subscription filters.
 */
// The same context as LibConsumerApplicationTests, which Spring reuses rather than starting another on the same port.
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServiceBusTests extends CftlibTest {

    private static final Duration WAIT = Duration.ofSeconds(30);

    // One shared connection: the emulator allows few concurrent connections.
    private final ServiceBusClientBuilder bus = new ServiceBusClientBuilder()
        .connectionString(System.getProperty("RSE_LIB_SERVICEBUS_CONNECTION_STRING"));

    @Test
    void deliversEachSessionsMessagesInOrderToOneReceiver() {
        var session = UUID.randomUUID().toString();
        try (var sender = bus.sender().queueName("cftlib-sessions").buildClient()) {
            for (var body : List.of("first", "second", "third")) {
                sender.sendMessage(new ServiceBusMessage(body).setSessionId(session));
            }
        }

        try (var sessions = bus.sessionReceiver().queueName("cftlib-sessions").buildClient();
             var receiver = sessions.acceptSession(session)) {
            var bodies = new ArrayList<String>();
            receiver.receiveMessages(3, WAIT).forEach(m -> {
                bodies.add(m.getBody().toString());
                receiver.complete(m);
            });
            assertThat(bodies, contains("first", "second", "third"));
        }
    }

    @Test
    void movesARejectedMessageToTheDeadLetterQueue() {
        var id = UUID.randomUUID().toString();
        try (var sender = bus.sender().queueName("cftlib-dead-letters").buildClient()) {
            sender.sendMessage(new ServiceBusMessage("unprocessable").setMessageId(id));
        }

        try (var receiver = bus.receiver().queueName("cftlib-dead-letters").buildClient()) {
            var message = receiveOne(receiver.receiveMessages(1, WAIT));
            receiver.deadLetter(message, new DeadLetterOptions().setDeadLetterReason("cftlib-test"));
        }

        try (var deadLetters = bus.receiver().queueName("cftlib-dead-letters").subQueue(SubQueue.DEAD_LETTER_QUEUE)
            .receiveMode(ServiceBusReceiveMode.RECEIVE_AND_DELETE).buildClient()) {
            var message = receiveOne(deadLetters.receiveMessages(1, WAIT));
            assertThat(message.getMessageId(), equalTo(id));
            assertThat(message.getDeadLetterReason(), equalTo("cftlib-test"));
        }
    }

    @Test
    void filtersATopicsMessagesByApplicationProperty() {
        try (var sender = bus.sender().topicName("cftlib-hearings").buildClient()) {
            var other = new ServiceBusMessage("other service");
            other.getApplicationProperties().put("hmctsServiceId", "ABA5");
            sender.sendMessage(other);
            var civil = new ServiceBusMessage("civil");
            civil.getApplicationProperties().put("hmctsServiceId", "AAA7");
            sender.sendMessage(civil);
        }

        try (var receiver = bus.receiver().topicName("cftlib-hearings").subscriptionName("cftlib-civil")
            .receiveMode(ServiceBusReceiveMode.RECEIVE_AND_DELETE).buildClient()) {
            assertThat(receiveOne(receiver.receiveMessages(1, WAIT)).getBody().toString(), equalTo("civil"));
            assertThat(receiver.peekMessage(), nullValue());
        }
    }

    private static ServiceBusReceivedMessage receiveOne(Iterable<ServiceBusReceivedMessage> messages) {
        var iterator = messages.iterator();
        assertThat("a message within " + WAIT, iterator.hasNext(), equalTo(true));
        return iterator.next();
    }
}
