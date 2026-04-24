package com.streamvault.command_service.domain.command;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record WithdrawMoneyCommand(
        @NotNull UUID accountId,
        @NotNull @Positive(message = "Withdraw amount must be greater than zero") BigDecimal amount,
        String description,
        UUID correlationId
        ) {
    public WithdrawMoneyCommand {
        if (correlationId == null)
            correlationId = UUID.randomUUID();
    }
}
