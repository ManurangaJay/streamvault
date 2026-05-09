package com.streamvault.query_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountSummaryResponse (
    UUID id,
    BigDecimal balance,
    String currency,
    String accountType,
    String status,
    Long transactionCount,
    Instant lastUpdatedAt
) {}
