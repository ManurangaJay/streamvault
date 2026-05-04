package com.streamvault.query_service.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class AccountCreated  extends BaseDomainEvent {
    private UUID ownerId;
    private String accountType;
    private String currency;
}
