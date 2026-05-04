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
@Table(name = "account_projections")
@Getter
@Setter
public class AccountProjection {
    @Id
    private UUID id;
    private UUID ownerId;
    private BigDecimal balance;
    private String currency;
    private String accountType;
    private String status;
    private Long transactionCount;
    private Instant lastUpdatedAt;
}
