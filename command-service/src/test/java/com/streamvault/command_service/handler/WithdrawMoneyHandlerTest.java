package com.streamvault.command_service.handler;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.streamvault.command_service.config.AppConfig;
import com.streamvault.command_service.domain.aggregate.Account;
import com.streamvault.command_service.domain.command.WithdrawMoneyCommand;
import com.streamvault.command_service.domain.entity.DomainEventRecord;
import com.streamvault.command_service.domain.enums.AccountStatus;
import com.streamvault.command_service.domain.enums.AccountType;
import com.streamvault.command_service.repository.AccountRepository;
import com.streamvault.command_service.repository.DomainEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.stream.function.StreamBridge;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WithdrawMoneyHandlerTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private DomainEventRepository domainEventRepository;

    @Mock
    private StreamBridge streamBridge;

    // Use a real ObjectMapper configured exactly like your app to avoid mocking JSON serialization
    @Spy
    private ObjectMapper objectMapper = new AppConfig().objectMapper();

    @InjectMocks
    private WithdrawMoneyHandler handler;

    private UUID accountId;
    private Account activeAccount;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        activeAccount = Account.builder()
                .id(accountId)
                .ownerId(UUID.randomUUID())
                .accountType(AccountType.SAVINGS)
                .currency("USD")
                .status(AccountStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void shouldSuccessfullyWithdraw_WhenBalanceIsSufficient() {
        // Given
        WithdrawMoneyCommand command = new WithdrawMoneyCommand(accountId, new BigDecimal("50.00"), "ATM", UUID.randomUUID());

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));

        // Mock the event stream: The account previously had $100 deposited
        DomainEventRecord depositRecord = DomainEventRecord.builder()
                .eventType("MoneyDeposited")
                .eventVersion(1L)
                .eventData(objectMapper.createObjectNode().put("amount", 100.00))
                .build();
        when(domainEventRepository.findByAggregateIdOrderByEventVersionAsc(accountId))
                .thenReturn(List.of(depositRecord));

        // When
        handler.handle(command);

        // Then
        ArgumentCaptor<DomainEventRecord> recordCaptor = ArgumentCaptor.forClass(DomainEventRecord.class);
        verify(domainEventRepository).save(recordCaptor.capture());

        DomainEventRecord savedRecord = recordCaptor.getValue();
        assertEquals("MoneyWithdrawn", savedRecord.getEventType());
        assertEquals(2L, savedRecord.getEventVersion()); // Version incremented

        verify(streamBridge).send(eq("transactionEvents-out-0"), any());
    }

    @Test
    void shouldThrowException_WhenInsufficientBalance() {
        // Given
        WithdrawMoneyCommand command = new WithdrawMoneyCommand(accountId, new BigDecimal("150.00"), "ATM", UUID.randomUUID());

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));

        // Mock the event stream: Only $100 exists in the account
        DomainEventRecord depositRecord = DomainEventRecord.builder()
                .eventType("MoneyDeposited")
                .eventVersion(1L)
                .eventData(objectMapper.createObjectNode().put("amount", 100.00))
                .build();
        when(domainEventRepository.findByAggregateIdOrderByEventVersionAsc(accountId))
                .thenReturn(List.of(depositRecord));

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> handler.handle(command));
        assertTrue(exception.getMessage().contains("Insufficient funds"));

        verify(domainEventRepository, never()).save(any());
        verify(streamBridge, never()).send(anyString(), any());
    }

    @Test
    void shouldThrowException_WhenAccountIsClosed() {
        // Given
        Account closedAccount = Account.builder()
                .id(accountId)
                .status(AccountStatus.CLOSED)
                .build();

        WithdrawMoneyCommand command = new WithdrawMoneyCommand(accountId, new BigDecimal("50.00"), "ATM", UUID.randomUUID());

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(closedAccount));

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> handler.handle(command));
        assertEquals("Cannot withdraw from an inactive or closed account.", exception.getMessage());

        verify(domainEventRepository, never()).findByAggregateIdOrderByEventVersionAsc(any());
    }
}