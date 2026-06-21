package com.streamvault.websocket_gateway.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamvault.websocket_gateway.service.SessionRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Set;
import java.util.function.Consumer;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class AccountEventConsumer {

    private final ObjectMapper objectMapper;
    private final SessionRegistryService sessionRegistry;
    private final SimpMessagingTemplate messagingTemplate;

    @Bean
    public Consumer<Message<String>> accountEventsConsumer() {
        return message -> {
            String eventType = message.getHeaders().get("eventType", String.class);
            String payload = message.getPayload();

            log.info("Received event type [{}] from account.events topic", eventType);

            try {
                EventEnvelope envelope = objectMapper.readValue(payload, EventEnvelope.class);
                String accountId = envelope.aggregateId();

                Set<String> activeSessions = sessionRegistry.getActiveSessions(accountId);

                if (activeSessions.isEmpty()) {
                    log.debug("No active WebSocket sessions found for Account [{}]. Dropping event.", accountId);
                    return;
                }

                log.info("Found {} active session(s) for Account [{}]. Pushing update...", activeSessions.size(), accountId);

                String destination = String.format("/topic/accounts/%s/transactions", accountId);
                messagingTemplate.convertAndSend(destination, envelope);

            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize Kafka message payload: {}", payload, e);
            } catch (Exception e) {
                log.error("Unexpected error processing account event", e);
            }
        };
    }
}