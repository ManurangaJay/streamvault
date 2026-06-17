package com.streamvault.command_service.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamvault.command_service.domain.aggregate.Account;
import com.streamvault.command_service.domain.command.DepositMoneyCommand;
import com.streamvault.command_service.domain.entity.DomainEventRecord;
import com.streamvault.command_service.domain.event.MoneyDeposited;
import com.streamvault.command_service.repository.AccountRepository;
import com.streamvault.command_service.repository.DomainEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DepositMoneyHandler {

    private final AccountRepository accountRepository;
    private final DomainEventRepository domainEventRepository;
    private final StreamBridge streamBridge;
    private final ObjectMapper objectMapper;

    @Transactional
    public void handle(DepositMoneyCommand command) {
        Account account = accountRepository.findById(command.accountId())
                .orElseThrow(() -> new IllegalArgumentException("Account not found with ID: " + command.accountId()));

        if(!account.isActive()) {
            throw new IllegalArgumentException("Cannot deposit into an inactive or closed account.");
        }

        Long currentVersion = domainEventRepository.findTopByAggregateIdOrderByEventVersionDesc(account.getId())
                .map(DomainEventRecord :: getEventVersion)
                .orElse(0L);
        Long nextVersion = currentVersion + 1;

        List<DomainEventRecord> eventStream = domainEventRepository.findByAggregateIdOrderByEventVersionAsc(account.getId());

        BigDecimal currentBalance = BigDecimal.ZERO;

        for (DomainEventRecord record : eventStream) {
            BigDecimal eventAmount = record.getEventData().has("amount")
                    ? record.getEventData().get("amount").decimalValue()
                    : BigDecimal.ZERO;

            switch (record.getEventType()) {
                case "MoneyDeposited":
                    currentBalance = currentBalance.add(eventAmount);
                    break;
                case "MoneyWithdrawn":
                    currentBalance = currentBalance.subtract(eventAmount);
                    break;
                case "MoneyTransferred":
                    UUID eventSourceId = UUID.fromString(record.getEventData().get("sourceAccountId").asText());
                    if (account.getId().equals(eventSourceId)) {
                        currentBalance = currentBalance.subtract(eventAmount);
                    } else {
                        currentBalance = currentBalance.add(eventAmount);
                    }
                    break;
            }
        }

        MoneyDeposited event = MoneyDeposited.builder()
                .eventId(UUID.randomUUID())
                .aggregateId(account.getId())
                .occurredAt(Instant.now())
                .version(nextVersion)
                .correlationId(command.correlationId())
                .amount(command.amount())
                .newBalance(currentBalance.add(command.amount()))
                .build();

        DomainEventRecord eventRecord = DomainEventRecord.builder()
                .aggregateId(event.getAggregateId())
                .aggregateType("Account")
                .eventType("MoneyDeposited")
                .eventData(objectMapper.valueToTree(event))
                .eventVersion(event.getVersion())
                .correlationId(event.getCorrelationId())
                .build();

        domainEventRepository.save(eventRecord);

        Message<MoneyDeposited> message = MessageBuilder
                .withPayload(event)
                        .setHeader("eventType", eventRecord.getEventType())
                                .setHeader("correlationId", event.getCorrelationId())
                                        .build();

        streamBridge.send("transactionEvents-out-0", message);
    }

}
