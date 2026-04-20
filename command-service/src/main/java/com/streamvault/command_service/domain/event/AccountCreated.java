package com.streamvault.command_service.domain.event;

import com.streamvault.command_service.domain.enums.AccountType;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Getter
@SuperBuilder
public class AccountCreated  extends BaseDomainEvent {
    private final UUID ownerId;
    private  final AccountType accountType;
    private  final String currency;
}
