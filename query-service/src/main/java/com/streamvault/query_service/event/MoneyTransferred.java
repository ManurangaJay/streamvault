package com.streamvault.query_service.event;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class MoneyTransferred extends BaseDomainEvent{
    private UUID targetAccountId;
    private BigDecimal amount;
}
