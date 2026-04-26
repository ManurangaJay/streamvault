package com.streamvault.command_service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.streamvault.command_service.domain.aggregate.Account;
import com.streamvault.command_service.domain.command.CreateAccountCommand;
import com.streamvault.command_service.domain.command.DepositMoneyCommand;
import com.streamvault.command_service.domain.entity.DomainEventRecord;
import com.streamvault.command_service.domain.entity.User;
import com.streamvault.command_service.domain.enums.AccountType;
import com.streamvault.command_service.handler.CreateAccountHandler;
import com.streamvault.command_service.handler.DepositMoneyHandler;
import com.streamvault.command_service.repository.AccountRepository;
import com.streamvault.command_service.repository.DomainEventRepository;
import com.streamvault.command_service.repository.UserRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class CommandFlowIntegrationTest {

    @Autowired
    private CreateAccountHandler createAccountHandler;

    @Autowired
    private DepositMoneyHandler depositMoneyHandler;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private DomainEventRepository domainEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Consumer<String, String> accountEventConsumer;
    private Consumer<String, String> transactionEventConsumer;
    private User testUser;

    @BeforeEach
    void setUp() {
        accountEventConsumer = createKafkaConsumer("account-test-group", "account.events");
        transactionEventConsumer = createKafkaConsumer("transaction-test-group", "transaction.events");

        testUser = User.builder()
                .fullName("Integration Test User")
                .email("test-" + UUID.randomUUID() + "@streamvault.com")
                .passwordHash("hashed")
                .build();

        testUser = userRepository.save(testUser);
    }

    @AfterEach
    void tearDown() {
        accountEventConsumer.close();
        transactionEventConsumer.close();

        domainEventRepository.deleteAllInBatch();
        accountRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void shouldExecuteCreateAccountFlow_AndPublishToKafka() throws Exception {
        UUID correlationId = UUID.randomUUID();
        CreateAccountCommand command = new CreateAccountCommand(testUser.getId(), AccountType.SAVINGS, "LKR", correlationId);

        UUID newAccountId = createAccountHandler.handle(command);

        Optional<Account> savedAccount = accountRepository.findById(newAccountId);
        assertTrue(savedAccount.isPresent());
        assertEquals(AccountType.SAVINGS, savedAccount.get().getAccountType());

        DomainEventRecord eventRecord = domainEventRepository.findTopByAggregateIdOrderByEventVersionDesc(newAccountId).orElseThrow();
        assertEquals("AccountCreated", eventRecord.getEventType());
        assertEquals(1L, eventRecord.getEventVersion());

        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(accountEventConsumer, Duration.ofSeconds(5));
        assertTrue(records.count() >= 1, "Should have published a message to account.events");

        // Find the specific message for this test run
        boolean messageFound = false;
        for (var record : records) {
            JsonNode jsonPayload = objectMapper.readTree(record.value());
            JsonNode aggregateIdNode = jsonPayload.get("aggregateId");

            // Only check the aggregateId, ignore eventType
            if (aggregateIdNode != null && newAccountId.toString().equals(aggregateIdNode.asText())) {
                // Assert on a field we know is in the AccountCreated POJO
                assertEquals("LKR", jsonPayload.get("currency").asText());
                messageFound = true;
                break;
            }
        }
        assertTrue(messageFound, "Could not find the expected Kafka message for this test run.");
    }

    @Test
    void shouldExecuteDepositFlow_AndPublishToTransactionTopic() throws Exception {
        UUID accountId = createAccountHandler.handle(
                new CreateAccountCommand(testUser.getId(), AccountType.CURRENT, "USD", UUID.randomUUID())
        );

        UUID correlationId = UUID.randomUUID();
        DepositMoneyCommand depositCommand = new DepositMoneyCommand(accountId, new BigDecimal("1500.00"), "Salary Deposit", correlationId);

        depositMoneyHandler.handle(depositCommand);

        DomainEventRecord eventRecord = domainEventRepository.findTopByAggregateIdOrderByEventVersionDesc(accountId).orElseThrow();
        assertEquals("MoneyDeposited", eventRecord.getEventType());
        assertEquals(2L, eventRecord.getEventVersion());

        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(transactionEventConsumer, Duration.ofSeconds(5));
        assertTrue(records.count() >= 1, "Should have published a message to transaction.events");

        boolean messageFound = false;
        for (var record : records) {
            JsonNode jsonPayload = objectMapper.readTree(record.value());
            JsonNode aggregateIdNode = jsonPayload.get("aggregateId");

            // Only check the aggregateId to identify the message
            if (aggregateIdNode != null && accountId.toString().equals(aggregateIdNode.asText())) {
                // Assert on the amount that we know is in the MoneyDeposited POJO
                assertEquals(1500.00, jsonPayload.get("amount").asDouble());
                messageFound = true;
                break;
            }
        }
        assertTrue(messageFound, "Could not find the expected Deposit Kafka message.");
    }

    private Consumer<String, String> createKafkaConsumer(String groupId, String topic) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        Consumer<String, String> consumer = new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer());
        consumer.subscribe(Collections.singletonList(topic));
        return consumer;
    }
}