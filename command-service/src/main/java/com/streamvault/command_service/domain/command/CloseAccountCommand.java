package com.streamvault.command_service.domain.command;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CloseAccountCommand(
        @NotNull UUID accountId,
        String reason,
        UUID correlationId
) {
    public CloseAccountCommand {
        if (correlationId == null) {
            correlationId = UUID.randomUUID();
        }
    }
}
