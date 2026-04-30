package com.streamvault.command_service.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamvault.command_service.domain.aggregate.Account;
import com.streamvault.command_service.domain.command.TransferMoneyCommand;
import com.streamvault.command_service.domain.entity.DomainEventRecord;
import com.streamvault.command_service.domain.entity.User;
import com.streamvault.command_service.domain.event.MoneyTransferred;
import com.streamvault.command_service.repository.AccountRepository;
import com.streamvault.command_service.repository.DomainEventRepository;
import com.streamvault.command_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransferMoneyHandler {

    private final AccountRepository accountRepository;
    private final DomainEventRepository domainEventRepository;
    private final StreamBridge streamBridge;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    @Transactional
    public void handle(TransferMoneyCommand command) {
        if (command.sourceAccountId().equals(command.targetAccountId())) {
            throw new IllegalArgumentException("Source and target accounts cannot be the same.");
        }

        Account sourceAccount = accountRepository.findById(command.sourceAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Source account not found"));
        Account targetAccount = accountRepository.findById(command.targetAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Target account not found"));

        String currentUserEmail = (String) Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getPrincipal();

        User currentUser = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found in database"));

        if (!currentUser.getId().equals(sourceAccount.getOwnerId())) {
            throw new AccessDeniedException("You are not authorized to transfer from this account");
        }

        if (!sourceAccount.isActive() || !targetAccount.isActive()) {
            throw new IllegalArgumentException("Both accounts must be active to perform a transfer");
        }

        List<DomainEventRecord> sourceEventStream = domainEventRepository.findByAggregateIdOrderByEventVersionAsc(sourceAccount.getId());

        BigDecimal currentBalance = BigDecimal.ZERO;
        Long sourceCurrentVersion = 0L;

        for (DomainEventRecord record : sourceEventStream) {
            sourceCurrentVersion = record.getEventVersion();
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
                    if (sourceAccount.getId().equals(eventSourceId)) {
                        currentBalance = currentBalance.subtract(eventAmount);
                    } else {
                        currentBalance = currentBalance.add(eventAmount);
                    }
                    break;
            }
        }
        if (currentBalance.compareTo(command.amount()) < 0) {
            throw new IllegalStateException("Insufficient funds. Current balance: " + currentBalance);
        }

        Long targetCurrentVersion = domainEventRepository.findTopByAggregateIdOrderByEventVersionDesc(targetAccount.getId())
                .map(DomainEventRecord :: getEventVersion)
                .orElse(0L);

        Instant timestamp = Instant.now();

        MoneyTransferred sourceEvent = MoneyTransferred.builder()
                .eventId(UUID.randomUUID())
                .aggregateId(sourceAccount.getId())
                .occurredAt(timestamp)
                .version(sourceCurrentVersion + 1)
                .correlationId(command.correlationId())
                .sourceAccountId(sourceAccount.getId())
                .targetAccountId(targetAccount.getId())
                .amount(command.amount())
                .description(command.description())
                .build();

        MoneyTransferred targetEvent = MoneyTransferred.builder()
                .eventId(UUID.randomUUID())
                .aggregateId(targetAccount.getId())
                .occurredAt(timestamp)
                .version(targetCurrentVersion + 1)
                .correlationId(command.correlationId())
                .sourceAccountId(sourceAccount.getId())
                .targetAccountId(targetAccount.getId())
                .amount(command.amount())
                .description(command.description())
                .build();

        domainEventRepository.save(buildEventRecord(sourceEvent, "MoneyTransferred"));
        domainEventRepository.save(buildEventRecord(targetEvent, "MoneyTransferred"));

        streamBridge.send("transactionEvents-out-0", sourceEvent);
        streamBridge.send("transactionEvents-out-0", targetEvent);
    }

    private DomainEventRecord buildEventRecord(MoneyTransferred event, String eventType) {
        return DomainEventRecord.builder()
                .aggregateId(event.getAggregateId())
                .aggregateType("Account")
                .eventType(eventType)
                .eventData(objectMapper.valueToTree(event))
                .eventVersion(event.getVersion())
                .correlationId(event.getCorrelationId())
                .build();
    }
}
