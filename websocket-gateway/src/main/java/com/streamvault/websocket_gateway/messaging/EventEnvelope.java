package com.streamvault.websocket_gateway.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EventEnvelope (
        String eventId,
        String eventType,
        String aggregateId,
        String aggregateType,
        String correlationId,
        String occurredAt,
        Integer version,
        BigDecimal newBalance,
        BigDecimal amount,
        String description
) {}
