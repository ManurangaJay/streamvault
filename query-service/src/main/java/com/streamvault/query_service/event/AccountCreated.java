package com.streamvault.query_service.event;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Getter
@Setter
@SuperBuilder
public class AccountCreated  extends BaseDomainEvent {
    private final UUID ownerId;
    private  final String accountType;
    private  final String currency;
}
