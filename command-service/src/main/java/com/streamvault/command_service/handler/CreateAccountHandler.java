package com.streamvault.command_service.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamvault.command_service.domain.aggregate.Account;
import com.streamvault.command_service.domain.command.CreateAccountCommand;
import com.streamvault.command_service.domain.entity.DomainEventRecord;
import com.streamvault.command_service.domain.entity.User;
import com.streamvault.command_service.domain.enums.AccountStatus;
import com.streamvault.command_service.domain.event.AccountCreated;
import com.streamvault.command_service.repository.AccountRepository;
import com.streamvault.command_service.repository.DomainEventRepository;
import com.streamvault.command_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateAccountHandler {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final DomainEventRepository domainEventRepository;
    private final StreamBridge streamBridge;
    private final ObjectMapper objectMapper;

    @Transactional
    public UUID handle(CreateAccountCommand command) {

        User owner = userRepository.findById(command.ownerId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + command.ownerId()));

        Account account = Account.builder()
                .id(UUID.randomUUID())
                .ownerId(owner.getId())
                .accountType(command.accountType())
                .currency(command.currency())
                .status(AccountStatus.ACTIVE)
                .build();

        accountRepository.save(account);

        AccountCreated event = AccountCreated.builder()
                .eventId(UUID.randomUUID())
                .aggregateId(account.getId())
                .occurredAT(Instant.now())
                .version(1L)
                .correlationId(command.correlationId())
                .ownerId(account.getOwnerId())
                .accountType(account.getAccountType())
                .currency(account.getCurrency())
                .build();

        DomainEventRecord eventRecord = DomainEventRecord.builder()
                .aggregateId(event.getAggregateId())
                .aggregateType("Account")
                .eventType("AccountCreated")
                .eventData(objectMapper.valueToTree(event))
                .eventVersion(event.getVersion())
                .correlationId(event.getCorrelationId())
                .build();

        domainEventRepository.save(eventRecord);

        streamBridge.send("accountEvents-out-0", event);

        return account.getId();
    }
}
