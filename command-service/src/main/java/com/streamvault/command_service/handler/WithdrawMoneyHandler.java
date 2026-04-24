package com.streamvault.command_service.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamvault.command_service.domain.aggregate.Account;
import com.streamvault.command_service.domain.command.WithdrawMoneyCommand;
import com.streamvault.command_service.domain.entity.DomainEventRecord;
import com.streamvault.command_service.domain.event.MoneyWithdrawn;
import com.streamvault.command_service.repository.AccountRepository;
import com.streamvault.command_service.repository.DomainEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WithdrawMoneyHandler {

    private final AccountRepository accountRepository;
    private final DomainEventRepository domainEventRepository;
    private final StreamBridge streamBridge;
    private final ObjectMapper objectMapper;

    public void handle(WithdrawMoneyCommand command) {
        Account account = accountRepository.findById(command.accountId())
                .orElseThrow(() -> new IllegalArgumentException("Account not found with ID: " + command.accountId()));

        if (!account.isActive()) {
            throw new IllegalArgumentException("Cannot withdraw from an inactive or closed account.");
        }

        List<DomainEventRecord> eventStream = domainEventRepository
                .findByAggregateIdOrderByEventVersionAsc(account.getId());

        BigDecimal currentBalance = BigDecimal.ZERO;
        Long currentVersion = 0L;

        for (DomainEventRecord record : eventStream) {
            currentVersion = record.getEventVersion();

            BigDecimal eventAmount = record.getEventData().has("amount")
                    ? record.getEventData().get("amount").decimalValue()
                    : BigDecimal.ZERO;

            switch (record.getEventType()) {
                case "MoneyDeposited" :
                    currentBalance = currentBalance.add(eventAmount);
                    break;
                case "MoneyWithdrawn" :
                    currentBalance = currentBalance.subtract(eventAmount);
                    break;
            }
        }

        if (currentBalance.compareTo(command.amount()) < 0) {
            throw new IllegalStateException("Insufficient funds. Current balance: " + currentBalance);
        }

        Long nextVersion = currentVersion + 1;

        MoneyWithdrawn event = MoneyWithdrawn.builder()
                .eventId(UUID.randomUUID())
                .aggregateId(account.getId())
                .occurredAt(Instant.now())
                .version(nextVersion)
                .correlationId(command.correlationId())
                .amount(command.amount())
                .build();

        DomainEventRecord eventRecord = DomainEventRecord.builder()
                .aggregateId(event.getAggregateId())
                .aggregateType("Account")
                .eventType("MoneyWithdrawn")
                .eventData(objectMapper.valueToTree(event))
                .eventVersion(event.getVersion())
                .correlationId(event.getCorrelationId())
                .build();
    }
}
