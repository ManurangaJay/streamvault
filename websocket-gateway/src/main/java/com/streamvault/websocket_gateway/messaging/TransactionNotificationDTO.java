package com.streamvault.websocket_gateway.messaging;

import java.math.BigDecimal;

public record TransactionNotificationDTO(
        String transactionId,
        String type,
        BigDecimal amount,
        String direction,
        String description
) {}