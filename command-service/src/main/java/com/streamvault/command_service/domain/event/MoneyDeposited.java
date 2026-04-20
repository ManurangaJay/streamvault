package com.streamvault.command_service.domain.event;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Getter
@SuperBuilder
public class MoneyDeposited extends BaseDomainEvent {
    private final BigDecimal amount;
}