package com.streamvault.query_service.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class MoneyWithdrawn extends BaseDomainEvent{
    private BigDecimal amount;
}
