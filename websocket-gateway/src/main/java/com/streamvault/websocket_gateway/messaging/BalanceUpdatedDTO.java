package com.streamvault.websocket_gateway.messaging;

import java.math.BigDecimal;

public record BalanceUpdatedDTO(
        BigDecimal balance,
        String changedAt,
        String direction,
        BigDecimal amount
) {
}
