package com.streamvault.query_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamvault.query_service.domain.AccountProjection;
import com.streamvault.query_service.event.MoneyDeposited;
import com.streamvault.query_service.repository.AccountProjectionRepository;
import com.streamvault.query_service.repository.TransactionProjectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer"
})
class ProjectionIntegrationTest {

    @Autowired
    private AccountProjectionRepository accountRepository;

    @Autowired
    private TransactionProjectionRepository transactionRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID accountId;

    @BeforeEach
    void setup() {
        // Seed the database with an initial Account
        accountId = UUID.randomUUID();
        AccountProjection initialAccount = new AccountProjection();
        initialAccount.setId(accountId);
        initialAccount.setOwnerId(UUID.randomUUID());
        initialAccount.setBalance(BigDecimal.ZERO);
        initialAccount.setCurrency("USD");
        initialAccount.setStatus("ACTIVE");
        initialAccount.setAccountType("SAVINGS");
        initialAccount.setTransactionCount(0L);
        initialAccount.setLastUpdatedAt(Instant.now());

        accountRepository.save(initialAccount);
    }

    @Test
    void shouldProcessMoneyDepositedEventAndUpdateProjections() throws Exception {
        // Create the raw event payload
        UUID eventId = UUID.randomUUID();
        MoneyDeposited event = new MoneyDeposited();
        event.setEventId(eventId);
        event.setAggregateId(accountId);
        event.setAmount(new BigDecimal("500.00"));
        event.setOccurredAt(Instant.now());
        event.setCorrelationId(UUID.randomUUID());

        String eventJson = objectMapper.writeValueAsString(event);

        // Publish the event to Kafka using MessageBuilder to attach the required headers
        Message<String> message = MessageBuilder
                .withPayload(eventJson)
                .setHeader(KafkaHeaders.TOPIC, "transaction.events")
                .setHeader("eventType", "MoneyDeposited")
                .setHeader("correlationId", event.getCorrelationId().toString())
                .build();

        kafkaTemplate.send(message);

        // Wait asynchronously for the consumer to process and update PostgreSQL & Redis
        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {

                    // Assert PostgreSQL Transaction History
                    long txCount = transactionRepository.findAll().stream()
                            .filter(tx -> tx.getAccountId().equals(accountId))
                            .count();
                    assertThat(txCount).isEqualTo(1L);

                    // Assert Redis Cache was populated
                    String redisKey = "balance::" + accountId;
                    String rawJson = stringRedisTemplate.opsForValue().get(redisKey);

                    assertThat(rawJson).isNotNull();

                    assertThat(rawJson).contains("500");
                });
    }
}