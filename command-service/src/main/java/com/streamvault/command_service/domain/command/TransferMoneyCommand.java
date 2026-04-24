package com.streamvault.command_service.domain.command;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferMoneyCommand(
        @NotNull UUID sourceAccountId,
        @NotNull UUID targetAccountId,
        @NotNull @Positive(message = "Transfer amount must be greater than zero") BigDecimal amount,
        String description,
        UUID correlationId
        ) {
    public TransferMoneyCommand {
        if (correlationId == null) {
            correlationId = UUID.randomUUID();
        }
    }
}
