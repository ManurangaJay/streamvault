package com.streamvault.command_service.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamvault.command_service.config.AppConfig;
import com.streamvault.command_service.domain.aggregate.Account;
import com.streamvault.command_service.domain.command.TransferMoneyCommand;
import com.streamvault.command_service.domain.entity.DomainEventRecord;
import com.streamvault.command_service.domain.enums.AccountStatus;
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
class TransferMoneyHandlerTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private DomainEventRepository domainEventRepository;

    @Mock
    private StreamBridge streamBridge;

    @Spy
    private ObjectMapper objectMapper = new AppConfig().objectMapper();

    @InjectMocks
    private TransferMoneyHandler handler;

    private UUID sourceAccountId;
    private UUID targetAccountId;
    private Account sourceAccount;
    private Account targetAccount;

    @BeforeEach
    void setUp() {
        sourceAccountId = UUID.randomUUID();
        targetAccountId = UUID.randomUUID();

        sourceAccount = Account.builder().id(sourceAccountId).status(AccountStatus.ACTIVE).build();
        targetAccount = Account.builder().id(targetAccountId).status(AccountStatus.ACTIVE).build();
    }

    @Test
    void shouldSuccessfullyTransfer_WhenFundsAreSufficient() {
        // Given
        TransferMoneyCommand command = new TransferMoneyCommand(sourceAccountId, targetAccountId, new BigDecimal("200.00"), "Rent", UUID.randomUUID());

        when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findById(targetAccountId)).thenReturn(Optional.of(targetAccount));

        // Mock source account history: Deposited $500
        DomainEventRecord deposit = DomainEventRecord.builder()
                .eventType("MoneyDeposited")
                .eventVersion(1L)
                .eventData(objectMapper.createObjectNode().put("amount", 500.00))
                .build();
        when(domainEventRepository.findByAggregateIdOrderByEventVersionAsc(sourceAccountId))
                .thenReturn(List.of(deposit));

        // Mock target account current version
        when(domainEventRepository.findTopByAggregateIdOrderByEventVersionDesc(targetAccountId))
                .thenReturn(Optional.empty()); // No previous events

        // When
        handler.handle(command);

        // Then
        ArgumentCaptor<DomainEventRecord> recordCaptor = ArgumentCaptor.forClass(DomainEventRecord.class);
        verify(domainEventRepository, times(2)).save(recordCaptor.capture());

        List<DomainEventRecord> savedRecords = recordCaptor.getAllValues();
        assertEquals(2, savedRecords.size());

        // Verify both events have the same correlation ID and type
        assertEquals("MoneyTransferred", savedRecords.get(0).getEventType());
        assertEquals("MoneyTransferred", savedRecords.get(1).getEventType());
        assertEquals(savedRecords.get(0).getCorrelationId(), savedRecords.get(1).getCorrelationId());

        verify(streamBridge, times(2)).send(eq("transactionEvents-out-0"), any());
    }

    @Test
    void shouldThrowException_WhenTransferringToSelf() {
        // Given
        TransferMoneyCommand command = new TransferMoneyCommand(sourceAccountId, sourceAccountId, new BigDecimal("100.00"), "Self", UUID.randomUUID());

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> handler.handle(command));
        assertEquals("Source and target accounts cannot be the same.", exception.getMessage());
    }

    @Test
    void shouldThrowException_WhenSourceHasInsufficientFunds() {
        // Given
        TransferMoneyCommand command = new TransferMoneyCommand(sourceAccountId, targetAccountId, new BigDecimal("1000.00"), "Car", UUID.randomUUID());

        when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findById(targetAccountId)).thenReturn(Optional.of(targetAccount));

        // Mock source account history: Deposited $500, Transferred out $100
        DomainEventRecord deposit = DomainEventRecord.builder()
                .eventType("MoneyDeposited")
                .eventData(objectMapper.createObjectNode().put("amount", 500.00))
                .build();
        DomainEventRecord priorTransfer = DomainEventRecord.builder()
                .eventType("MoneyTransferred")
                .eventData(objectMapper.createObjectNode()
                        .put("amount", 100.00)
                        .put("sourceAccountId", sourceAccountId.toString())) // Indicates money left the account
                .build();

        when(domainEventRepository.findByAggregateIdOrderByEventVersionAsc(sourceAccountId))
                .thenReturn(List.of(deposit, priorTransfer)); // Current balance = $400

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> handler.handle(command));
        assertTrue(exception.getMessage().contains("Insufficient funds"));

        verify(domainEventRepository, never()).save(any());
    }
}