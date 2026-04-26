package com.streamvault.command_service.handler;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.streamvault.command_service.config.AppConfig;
import com.streamvault.command_service.domain.aggregate.Account;
import com.streamvault.command_service.domain.command.CreateAccountCommand;
import com.streamvault.command_service.domain.entity.User;
import com.streamvault.command_service.domain.enums.AccountType;
import com.streamvault.command_service.repository.AccountRepository;
import com.streamvault.command_service.repository.DomainEventRepository;
import com.streamvault.command_service.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateAccountHandlerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private DomainEventRepository domainEventRepository;

    @Mock
    private StreamBridge streamBridge;

    @Spy
    private ObjectMapper objectMapper = new AppConfig().objectMapper();

    @InjectMocks
    private CreateAccountHandler handler;

    @Test
    void shouldSuccessfullyCreateAccount() {
        // Given
        UUID ownerId = UUID.randomUUID();
        CreateAccountCommand command = new CreateAccountCommand(ownerId, AccountType.CURRENT, "USD", UUID.randomUUID());

        User mockUser = User.builder().id(ownerId).build();
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(mockUser));

        // When
        UUID newAccountId = handler.handle(command);

        // Then
        assertNotNull(newAccountId);
        verify(accountRepository).save(any(Account.class));
        verify(domainEventRepository).save(any());
        verify(streamBridge).send(eq("accountEvents-out-0"), any());
    }

    @Test
    void shouldThrowException_WhenUserDoesNotExist() {
        // Given
        UUID ownerId = UUID.randomUUID();
        CreateAccountCommand command = new CreateAccountCommand(ownerId, AccountType.CURRENT, "USD", UUID.randomUUID());

        when(userRepository.findById(ownerId)).thenReturn(Optional.empty());

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> handler.handle(command));
        assertTrue(exception.getMessage().contains("User not found"));

        verify(accountRepository, never()).save(any());
    }

    @Test
    void shouldHandleIdempotency_WhenDuplicateCommandArrives() {
        // Given
        UUID ownerId = UUID.randomUUID();
        CreateAccountCommand command = new CreateAccountCommand(ownerId, AccountType.CURRENT, "USD", UUID.randomUUID());

        User mockUser = User.builder().id(ownerId).build();
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(mockUser));

        // Simulate the database rejecting the duplicate event due to the UNIQUE(aggregate_id, event_version) constraint
        when(domainEventRepository.save(any())).thenThrow(new DataIntegrityViolationException("Duplicate event version"));

        // When & Then
        assertThrows(DataIntegrityViolationException.class, () -> handler.handle(command));

        // Ensure Kafka message is NEVER sent if the database save fails
        verify(streamBridge, never()).send(anyString(), any());
    }
}