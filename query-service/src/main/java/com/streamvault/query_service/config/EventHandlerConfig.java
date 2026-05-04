package com.streamvault.query_service.config;

import org.apache.kafka.clients.consumer.internals.Acknowledgements;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

@Configuration
public class EventHandlerConfig {

    @Bean
    public Consumer<Message<String>> transactionEvents() {
        return message -> {
            try {
                String eventPayload = message.getPayload();
                System.out.println("Processing transaction event: " + eventPayload);
                Acknowledgment acknowledgement = message.getHeaders().get(KafkaHeaders.ACKNOWLEDGMENT, Acknowledgment.class);
                if (acknowledgement != null) {
                    acknowledgement.acknowledge();
                }
            } catch (Exception e) {
                System.err.println("Failed to process event, offset not committed.");
            }
        };
    }

    @Bean
    public Consumer<Message<String>> accountEvents() {
        return message -> {
            Acknowledgment acknowledgment = message.getHeaders().get(KafkaHeaders.ACKNOWLEDGMENT, Acknowledgment.class);
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
        };
    }
}
