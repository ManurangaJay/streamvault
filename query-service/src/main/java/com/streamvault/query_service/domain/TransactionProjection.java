package com.streamvault.query_service.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transaction_projections")
@Getter
@Setter
public class TransactionProjection {

    @Id
    private UUID id;
    private UUID accountId;
    private String eventType;
    private BigDecimal amount;
    private String direction;
    private BigDecimal balanceAfter;
    private String description;
    private Instant createdAt;
    private UUID correlationId;
}
