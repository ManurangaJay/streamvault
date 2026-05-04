package com.streamvault.query_service.event;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@SuperBuilder
public abstract class BaseDomainEvent {
    private final UUID eventId;
    private final UUID aggregateId;
    private final Instant occurredAt;
    private final Long version;
    private final UUID correlationId;
}
