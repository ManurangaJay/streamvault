package com.streamvault.command_service.domain.command;

import com.streamvault.command_service.domain.enums.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateAccountCommand(
        @NotNull UUID ownerId,
        @NotNull AccountType accountType,
        @NotBlank String currency,
        UUID correlationId
        ) {
    public CreateAccountCommand {
        if (correlationId == null) {
            correlationId = UUID.randomUUID();
        }
    }
}
