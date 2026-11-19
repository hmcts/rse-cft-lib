package uk.gov.hmcts.rse.ccd.lib.hmc.hearingservice;

import static uk.gov.hmcts.reform.hmc.constants.Constants.CFT_HEARING_SERVICE;
import static uk.gov.hmcts.reform.hmc.constants.Constants.ERROR_PROCESSING_MESSAGE;
import static uk.gov.hmcts.reform.hmc.constants.Constants.ERROR_SENDING_MESSAGE;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HEARING_ID;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMCTS_DEPLOYMENT_ID;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMCTS_SERVICE_ID;
import static uk.gov.hmcts.reform.hmc.constants.Constants.HMC_TO_HMI;
import static uk.gov.hmcts.reform.hmc.constants.Constants.MESSAGE_TYPE;
import static uk.gov.hmcts.reform.hmc.constants.Constants.TOPIC_HMC_TO_CFT;
import static uk.gov.hmcts.reform.hmc.constants.Constants.WRITE;

import com.azure.messaging.servicebus.ServiceBusMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.hmc.ApplicationParams;
import uk.gov.hmcts.reform.hmc.config.MessageSenderToQueueConfiguration;
import uk.gov.hmcts.reform.hmc.config.MessageSenderToTopicConfiguration;
import uk.gov.hmcts.rse.ccd.lib.hmc.ServiceBusSenders;

/**
 * Replaces hmc-cft-hearing-service's senders, for the hmc-to-hmi queue and the hmc-to-cft topic, with ones that
 * reuse one sender each. See {@link ServiceBusSenders}.
 */
@Component
@ConditionalOnClass(name = "uk.gov.hmcts.reform.hmc.config.MessageSenderToQueueConfiguration")
public class HearingServiceSenders implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(HearingServiceSenders.class);

    private final ObjectProvider<ApplicationParams> params;

    public HearingServiceSenders(ObjectProvider<ApplicationParams> params) {
        this.params = params;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof MessageSenderToTopicConfiguration && !(bean instanceof TopicSender)) {
            return new TopicSender(params.getObject());
        }
        if (bean instanceof MessageSenderToQueueConfiguration && !(bean instanceof QueueSender)) {
            return new QueueSender(params.getObject());
        }
        return bean;
    }

    // As MessageSenderToTopicConfiguration, but reusing its sender.
    static class TopicSender extends MessageSenderToTopicConfiguration {
        private final ApplicationParams params;

        TopicSender(ApplicationParams params) {
            super(params);
            this.params = params;
        }

        @Override
        public void sendMessage(String message, String hmctsServiceId, String hearingId, String deploymentId) {
            try {
                ServiceBusMessage serviceBusMessage = new ServiceBusMessage(message);
                serviceBusMessage.getApplicationProperties().put(HMCTS_SERVICE_ID, hmctsServiceId);
                serviceBusMessage.getApplicationProperties().put(HEARING_ID, hearingId);
                if (deploymentId != null && !deploymentId.isEmpty()) {
                    serviceBusMessage.getApplicationProperties().put(HMCTS_DEPLOYMENT_ID, deploymentId);
                }
                ServiceBusSenders.sendToTopic(params.getExternalConnectionString(), params.getExternalTopicName(),
                    serviceBusMessage);
            } catch (Exception e) {
                log.error("Error while sending the message to topic:{}", e.getMessage());
                log.error(ERROR_SENDING_MESSAGE, CFT_HEARING_SERVICE, TOPIC_HMC_TO_CFT, WRITE, hearingId);
            }
        }
    }

    // As MessageSenderToQueueConfiguration, but reusing its sender.
    static class QueueSender extends MessageSenderToQueueConfiguration {
        private final ApplicationParams params;

        QueueSender(ApplicationParams params) {
            super(params);
            this.params = params;
        }

        @Override
        public void sendMessageToQueue(String message, Long hearingId, String messageType, String deploymentId) {
            try {
                ServiceBusMessage serviceBusMessage = new ServiceBusMessage(message);
                serviceBusMessage.getApplicationProperties().put(MESSAGE_TYPE, messageType);
                serviceBusMessage.getApplicationProperties().put(HEARING_ID, hearingId);
                if (deploymentId != null && !deploymentId.isEmpty()) {
                    serviceBusMessage.getApplicationProperties().put(HMCTS_DEPLOYMENT_ID, deploymentId);
                }
                ServiceBusSenders.sendToQueue(params.getInternalOutboundConnectionString(),
                    params.getInternalOutboundQueueName(), serviceBusMessage);
            } catch (Exception e) {
                log.error(ERROR_PROCESSING_MESSAGE, CFT_HEARING_SERVICE, HMC_TO_HMI, WRITE, hearingId);
                log.error("Error while sending the message to queue:{}", e.getMessage());
            }
        }
    }
}
