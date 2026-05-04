package com.streamvault.query_service.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Getter
@Setter
public class MoneyDeposited extends BaseDomainEvent{
    private BigDecimal amount;
}
