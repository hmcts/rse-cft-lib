package uk.gov.hmcts.rse.ccd.lib.hmc.inboundadapter;

import static uk.gov.hmcts.reform.hmc.constants.Constants.ERROR_PROCESSING_MESSAGE;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMC_FROM_HMI;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMC_HMI_INBOUND_ADAPTER;
import static uk.gov.hmcts.reform.hmc.constants.Constants.WRITE;

import com.azure.messaging.servicebus.ServiceBusMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.hmc.ApplicationParams;
import uk.gov.hmcts.reform.hmc.config.MessageSenderConfiguration;
import uk.gov.hmcts.reform.hmc.config.MessageType;
import uk.gov.hmcts.rse.ccd.lib.hmc.ServiceBusSenders;

/**
 * Replaces hmc-hmi-inbound-adapter's sender, for the hmc-from-hmi queue, with one that reuses its sender. See
 * {@link ServiceBusSenders}.
 */
@Component
@ConditionalOnClass(name = "uk.gov.hmcts.reform.hmc.controllers.HmcInboundController")
public class InboundAdapterSenders implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(InboundAdapterSenders.class);

    private final ObjectProvider<ApplicationParams> params;

    public InboundAdapterSenders(ObjectProvider<ApplicationParams> params) {
        this.params = params;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof MessageSenderConfiguration && !(bean instanceof QueueSender)) {
            return new QueueSender(params.getObject());
        }
        return bean;
    }

    // As MessageSenderConfiguration, but reusing its sender.
    static class QueueSender extends MessageSenderConfiguration {
        private final ApplicationParams params;

        QueueSender(ApplicationParams params) {
            super(params);
            this.params = params;
        }

        @Override
        public void sendMessage(String message, MessageType messageType, String hearingId) {
            try {
                ServiceBusMessage serviceBusMessage = new ServiceBusMessage(message);
                serviceBusMessage.getApplicationProperties().put("message_type", messageType.name());
                serviceBusMessage.getApplicationProperties().put("hearing_id", hearingId);
                ServiceBusSenders.sendToQueue(params.getConnectionString(), params.getQueueName(), serviceBusMessage);
            } catch (Exception e) {
                log.error("Error while sending the message to queue:{}", e.getMessage());
                log.error(ERROR_PROCESSING_MESSAGE, HMC_HMI_INBOUND_ADAPTER, HMC_FROM_HMI, WRITE, hearingId);
            }
        }
    }
}
