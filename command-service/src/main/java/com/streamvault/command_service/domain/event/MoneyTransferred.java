package com.streamvault.command_service.domain.event;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@SuperBuilder
public class MoneyTransferred extends BaseDomainEvent {
    private final UUID targetAccount;
    private final BigDecimal amount;
}
