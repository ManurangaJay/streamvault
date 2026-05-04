package com.streamvault.query_service.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public abstract class BaseDomainEvent {
    private UUID eventId;
    private UUID aggregateId;
    private Instant occurredAt;
    private Long version;
    private UUID correlationId;
}
