package com.streamvault.query_service.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamvault.query_service.event.MoneyDeposited;
import com.streamvault.query_service.event.MoneyTransferred;
import com.streamvault.query_service.event.MoneyWithdrawn;
import com.streamvault.query_service.service.ProjectionUpdaterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class TransactionEventHandlerConfig {

    private final ObjectMapper objectMapper;
    private final ProjectionUpdaterService projectionUpdaterService;

    @Bean
    public Consumer<Message<String>> transactionEvents() {
        return message -> {
            try {
                String eventType = message.getHeaders().get("eventType", String.class);
                String payload = message.getPayload();

                if (eventType == null) {
                    log.warn("Received message without 'eventType' header. Payload: {}", payload);
                    acknowledge(message);
                    return;
                }

                switch (eventType) {
                    case "MoneyDeposited" -> {
                        MoneyDeposited event = objectMapper.readValue(payload, MoneyDeposited.class);
                        projectionUpdaterService.processMoneyDeposited(event);
                    }
                    case "MoneyWithdrawn" -> {
                        MoneyWithdrawn event = objectMapper.readValue(payload, MoneyWithdrawn.class);
                        projectionUpdaterService.processMoneyWithdrawn(event);
                    }
                    case "MoneyTransferred" -> {
                        MoneyTransferred event = objectMapper.readValue(payload, MoneyTransferred.class);
                        projectionUpdaterService.processMoneyTransferred(event);
                    }
                    default -> log.warn("Unknown eventType received in header: {}", eventType);
                }
                acknowledge(message);

            } catch (Exception e) {
                log.error("Failed to process transaction event. Offset not committed.", e);
                throw new RuntimeException("Event processing failed", e);
            }
        };
    }

    private void acknowledge(Message<?> message) {
        Acknowledgment acknowledgment = message.getHeaders().get(KafkaHeaders.ACKNOWLEDGMENT, Acknowledgment.class);
        if (acknowledgment != null) {
            acknowledgment.acknowledge();
        }
    }
}