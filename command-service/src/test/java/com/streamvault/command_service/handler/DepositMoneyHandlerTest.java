package com.streamvault.command_service.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamvault.command_service.config.AppConfig;
import com.streamvault.command_service.domain.aggregate.Account;
import com.streamvault.command_service.domain.command.DepositMoneyCommand;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepositMoneyHandlerTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private DomainEventRepository domainEventRepository;

    @Mock
    private StreamBridge streamBridge;

    @Spy
    private ObjectMapper objectMapper = new AppConfig().objectMapper();

    @InjectMocks
    private DepositMoneyHandler handler;

    private UUID accountId;
    private Account activeAccount;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        activeAccount = Account.builder()
                .id(accountId)
                .status(AccountStatus.ACTIVE)
                .build();
    }

    @Test
    void shouldSuccessfullyDepositMoney() {
        // Given
        DepositMoneyCommand command = new DepositMoneyCommand(accountId, new BigDecimal("100.00"), "ATM", UUID.randomUUID());

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));

        // Mock current event stream version (e.g., account was just created, version = 1)
        DomainEventRecord lastEvent = DomainEventRecord.builder().eventVersion(1L).build();
        when(domainEventRepository.findTopByAggregateIdOrderByEventVersionDesc(accountId))
                .thenReturn(Optional.of(lastEvent));

        // When
        handler.handle(command);

        // Then
        ArgumentCaptor<DomainEventRecord> recordCaptor = ArgumentCaptor.forClass(DomainEventRecord.class);
        verify(domainEventRepository).save(recordCaptor.capture());

        DomainEventRecord savedRecord = recordCaptor.getValue();
        assertEquals("MoneyDeposited", savedRecord.getEventType());
        assertEquals(2L, savedRecord.getEventVersion()); // Version should increment
        assertEquals(100.00, savedRecord.getEventData().get("amount").asDouble());

        verify(streamBridge).send(eq("transactionEvents-out-0"), any());
    }

    @Test
    void shouldThrowException_WhenAccountIsClosed() {
        // Given
        Account closedAccount = Account.builder()
                .id(accountId)
                .status(AccountStatus.CLOSED)
                .build();

        DepositMoneyCommand command = new DepositMoneyCommand(accountId, new BigDecimal("100.00"), "ATM", UUID.randomUUID());

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(closedAccount));

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> handler.handle(command));
        assertEquals("Cannot deposit into an inactive or closed account.", exception.getMessage());

        verify(domainEventRepository, never()).save(any());
        verify(streamBridge, never()).send(anyString(), any());
    }
}