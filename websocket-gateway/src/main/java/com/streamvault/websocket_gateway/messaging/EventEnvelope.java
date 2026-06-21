package com.streamvault.websocket_gateway.messaging;


import java.math.BigDecimal;

public record EventEnvelope (
        String eventId,
        String eventType,
        String aggregateId,
        String aggregateType,
        String correlationId,
        String occurredAt,
        Integer version,
        BigDecimal newBalance
) {}
