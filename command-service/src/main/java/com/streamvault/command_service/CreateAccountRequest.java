package com.streamvault.command_service;

import com.streamvault.command_service.domain.enums.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateAccountRequest(
        @NotNull AccountType accountType,
        @NotBlank String currency,
        UUID correlationId
) {
}
