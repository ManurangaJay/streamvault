package com.streamvault.command_service.domain.command;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record DepositMoneyCommand(
        @NotNull UUID accountId,
        @NotNull @Positive(message = "Deposit amount must be greater than zero") BigDecimal amount,
        String description,
        UUID correlationId
        ) {
    public DepositMoneyCommand {
        if(correlationId == null) {
            correlationId = UUID.randomUUID();
        }
    }
}
