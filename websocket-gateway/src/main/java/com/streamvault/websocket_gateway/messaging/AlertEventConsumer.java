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
public class AlertEventConsumer {

    private final ObjectMapper objectMapper;
    private final SessionRegistryService sessionRegistry;
    private final SimpMessagingTemplate messagingTemplate;

    @Bean
    public Consumer<Message<String>> alertEventsConsumer() {
        return message -> {
            String eventType = message.getHeaders().get("eventType", String.class);
            String payload = message.getPayload();

            try {
                EventEnvelope envelope = objectMapper.readValue(payload, EventEnvelope.class);
                String accountId = envelope.aggregateId();

                Set<String> activeSessions = sessionRegistry.getActiveSessions(accountId);

                if (activeSessions.isEmpty()) {
                    return;
                }

                log.warn("Pushing [{}] alert for Account [{}] to {} session(s)", eventType, accountId, activeSessions.size());

                String severity = determineSeverity(eventType);
                String displayMessage = generateAlertMessage(eventType, envelope);

                AlertNotificationDTO alertPayload = new AlertNotificationDTO(
                        envelope.eventId(),
                        eventType,
                        displayMessage,
                        severity,
                        envelope.occurredAt()
                );

                String destination = String.format("/topic/accounts/%s/alerts", accountId);
                messagingTemplate.convertAndSend(destination, alertPayload);

            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize Kafka alert payload: {}", payload, e);
            } catch (Exception e) {
                log.error("Unexpected error processing alert event", e);
            }
        };
    }

    private String determineSeverity(String eventType) {
        if (eventType == null) return "INFO";
        return switch (eventType) {
            case "OverdraftAttempted" -> "WARNING";
            case "AccountSuspended", "FraudDetected" -> "CRITICAL";
            default -> "INFO";
        };
    }

    private String generateAlertMessage(String eventType, EventEnvelope envelope) {
        if (eventType == null) return "A system alert was triggered on your account.";

        return switch (eventType) {
            case "OverdraftAttempted" -> String.format(
                    "Declined: Attempted to withdraw %s but balance is insufficient.",
                    envelope.amount() != null ? "$" + envelope.amount() : "funds"
            );
            case "AccountSuspended" -> "CRITICAL: This account has been suspended. Please contact support.";
            default -> "Important update regarding your account status.";
        };
    }
}