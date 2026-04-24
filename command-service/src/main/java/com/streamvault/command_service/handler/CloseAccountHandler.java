package com.streamvault.command_service.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamvault.command_service.domain.aggregate.Account;
import com.streamvault.command_service.domain.command.CloseAccountCommand;
import com.streamvault.command_service.domain.entity.DomainEventRecord;
import com.streamvault.command_service.domain.enums.AccountStatus;
import com.streamvault.command_service.domain.event.AccountClosed;
import com.streamvault.command_service.repository.AccountRepository;
import com.streamvault.command_service.repository.DomainEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CloseAccountHandler {

    private final AccountRepository accountRepository;
    private final DomainEventRepository domainEventRepository;
    private final StreamBridge streamBridge;
    private final ObjectMapper objectMapper;

    @Transactional
    public void handle(CloseAccountCommand command) {
        Account account = accountRepository.findById(command.accountId())
                .orElseThrow(() -> new IllegalArgumentException("Account not found with ID: " + command.accountId()));

        if (!account.isActive()) {
            throw new IllegalStateException("Account is already closed or suspended.");
        }

        List<DomainEventRecord> eventStream = domainEventRepository.findByAggregateIdOrderByEventVersionAsc(account.getId());

        BigDecimal currentBalance = BigDecimal.ZERO;
        Long currentVersion = 0L;

        for (DomainEventRecord record : eventStream) {
            currentVersion = record.getEventVersion();

            BigDecimal eventAmount = record.getEventData().has("amount")
                    ? record.getEventData().get("amount").decimalValue()
                    : BigDecimal.ZERO;

            switch (record.getEventType()) {
                case "MoneyDeposited":
                    currentBalance = currentBalance.add(eventAmount);
                case "MoneyWithdrawn":
                    currentBalance = currentBalance.subtract(eventAmount);
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
        if (currentBalance.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalStateException("Account cannot be closed because it has a non-zero balance: " + currentBalance);
        }

        account.updateStatus(AccountStatus.CLOSED);
        accountRepository.save(account);

        AccountClosed event = AccountClosed.builder()
                .eventId(UUID.randomUUID())
                .aggregateId(account.getId())
                .occurredAt(Instant.now())
                .version(currentVersion + 1)
                .correlationId(command.correlationId())
                .reason(command.reason())
                .build();

        DomainEventRecord eventRecord = DomainEventRecord.builder()
                .aggregateId(event.getAggregateId())
                .aggregateType("Account")
                .eventType("AccountClosed")
                .eventData(objectMapper.valueToTree(event))
                .eventVersion(event.getVersion())
                .correlationId(event.getCorrelationId())
                .build();

        domainEventRepository.save(eventRecord);

        streamBridge.send("accountEvents-out-0", event);
    }
}
