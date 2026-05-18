package com.streamvault.query_service.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamvault.query_service.event.*;
import com.streamvault.query_service.repository.AccountProjectionRepository;
import com.streamvault.query_service.service.ProjectionUpdaterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class AccountEventHandlerConfig {

    private final ObjectMapper objectMapper;
    private final ProjectionUpdaterService projectionUpdaterService;

    @Bean
    public Consumer<Message<String>> accountEvents() {
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
                  case "AccountCreated" -> {
                      AccountCreated event = objectMapper.readValue(payload, AccountCreated.class);
                      projectionUpdaterService.processAccountCreated(event);
                  }
                  case "AccountClosed" -> {
                      AccountClosed event = objectMapper.readValue(payload, AccountClosed.class);
                      projectionUpdaterService.processAccountClosed(event);
                  }
                  default -> log.warn("Unknown eventType received in header: {}", eventType);
              }
              acknowledge(message);

          } catch (Exception e) {
              log.error("Failed to process transaction event. Offset not committed.", e);
              throw new RuntimeException("Event processing failed", e);          }
        };
    }
    private void acknowledge(Message<?> message) {
        Acknowledgment acknowledgment = message.getHeaders().get(KafkaHeaders.ACKNOWLEDGMENT, Acknowledgment.class);
        if (acknowledgment != null) {
            acknowledgment.acknowledge();
        }
    }
}
