package com.streamvault.command_service.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamvault.command_service.config.AppConfig;
import com.streamvault.command_service.domain.aggregate.Account;
import com.streamvault.command_service.domain.command.CloseAccountCommand;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CloseAccountHandlerTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private DomainEventRepository domainEventRepository;

    @Mock
    private StreamBridge streamBridge;

    @Spy
    private ObjectMapper objectMapper = new AppConfig().objectMapper();

    @InjectMocks
    private CloseAccountHandler handler;

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
    void shouldSuccessfullyCloseAccount_WhenBalanceIsZero() {
        // Given
        CloseAccountCommand command = new CloseAccountCommand(accountId, "Switching banks", UUID.randomUUID());

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));

        // Mock event stream resulting in 0 balance ($100 deposited, $100 withdrawn)
        DomainEventRecord deposit = DomainEventRecord.builder()
                .eventType("MoneyDeposited")
                .eventVersion(1L)
                .eventData(objectMapper.createObjectNode().put("amount", 100.00))
                .build();
        DomainEventRecord withdrawal = DomainEventRecord.builder()
                .eventType("MoneyWithdrawn")
                .eventVersion(2L)
                .eventData(objectMapper.createObjectNode().put("amount", 100.00))
                .build();

        when(domainEventRepository.findByAggregateIdOrderByEventVersionAsc(accountId))
                .thenReturn(List.of(deposit, withdrawal));

        // When
        handler.handle(command);

        // Then
        assertEquals(AccountStatus.CLOSED, activeAccount.getStatus()); // Verify mutable entity was updated
        verify(accountRepository).save(activeAccount);

        ArgumentCaptor<DomainEventRecord> recordCaptor = ArgumentCaptor.forClass(DomainEventRecord.class);
        verify(domainEventRepository).save(recordCaptor.capture());
        assertEquals("AccountClosed", recordCaptor.getValue().getEventType());
        assertEquals(3L, recordCaptor.getValue().getEventVersion());

        verify(streamBridge).send(eq("accountEvents-out-0"), any());
    }

    @Test
    void shouldThrowException_WhenAccountHasNonZeroBalance() {
        // Given
        CloseAccountCommand command = new CloseAccountCommand(accountId, "Not needed", UUID.randomUUID());

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));

        // Mock event stream resulting in positive balance
        DomainEventRecord deposit = DomainEventRecord.builder()
                .eventType("MoneyDeposited")
                .eventVersion(1L)
                .eventData(objectMapper.createObjectNode().put("amount", 50.00))
                .build();

        when(domainEventRepository.findByAggregateIdOrderByEventVersionAsc(accountId))
                .thenReturn(List.of(deposit));

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> handler.handle(command));
        assertTrue(exception.getMessage().contains("non-zero balance"));

        verify(accountRepository, never()).save(any()); // Should not update status
        verify(domainEventRepository, never()).save(any());
    }
}