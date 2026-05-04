package com.streamvault.query_service.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamvault.query_service.domain.AccountProjection;
import com.streamvault.query_service.event.AccountCreated;
import com.streamvault.query_service.repository.AccountProjectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class AccountEventHandlerConfig {

    private final AccountProjectionRepository repository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Bean
    public Consumer<Message<String>> accountEvents() {
        return message -> {
          try {
              String payload = message.getPayload();

              JsonNode rootNode = objectMapper.readTree(payload);
              String eventType = rootNode.path("eventType").asText();

              if (eventType.equals("AccountCreated")) {
                  AccountCreated event = objectMapper.readValue(payload, AccountCreated.class);

                  AccountProjection projection = new AccountProjection();
                  projection.setId(event.getAggregateId());
                  projection.setOwnerId(rootNode.has("payload") ?
                          UUID.fromString(rootNode.get("payload").get("ownerId").asText()) : event.getOwnerId());
                  projection.setBalance(BigDecimal.ZERO);
                  projection.setCurrency(rootNode.has("payload") ?
                          rootNode.get("payload").get("accountType").asText() : event.getAccountType());
                  projection.setStatus("ACTIVE");
                  projection.setTransactionCount(0L);
                  projection.setLastUpdatedAt(event.getOccurredAt());

                  repository.save(projection);
                  log.info("PostgreSQL projection created for account: {}", event.getAggregateId());

                  String redisKey = "balance::" + event.getAggregateId();

                  Map<String, Object> balanceCache = Map.of(
                          "balance", BigDecimal.ZERO,
                          "currency", projection.getCurrency(),
                          "lastUpdated", event.getOccurredAt()
                  );

                  redisTemplate.opsForValue().set(redisKey, balanceCache);
                  log.info("Redis cache initialized at {} for account: {}", redisKey, event.getAggregateId());
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
