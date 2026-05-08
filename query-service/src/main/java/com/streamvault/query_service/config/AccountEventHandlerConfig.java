package com.streamvault.query_service.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamvault.query_service.event.AccountClosed;
import com.streamvault.query_service.event.AccountCreated;
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

    private final AccountProjectionRepository repository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ProjectionUpdaterService projectionUpdaterService;

    @Bean
    public Consumer<Message<String>> accountEvents() {
        return message -> {
          try {
              String payload = message.getPayload();

              JsonNode rootNode = objectMapper.readTree(payload);
              String eventType = rootNode.path("eventType").asText();

              if (eventType.equals("AccountCreated")) {
                  AccountCreated event = objectMapper.readValue(payload, AccountCreated.class);
                  projectionUpdaterService.processAccountCreated(event);
              } else if ("AccountClosed".equals(eventType)) {
                  AccountClosed event = objectMapper.readValue(payload, AccountClosed.class);
                  projectionUpdaterService.processAccountClosed(event);
              }

              Acknowledgment ack = message.getHeaders().get(KafkaHeaders.ACKNOWLEDGMENT, Acknowledgment.class);
              if (ack != null) {
                  ack.acknowledge();
              }

          } catch (Exception e) {
                log.error("Failed to process account event. Offset not committed.");
          }
        };
    }
}
