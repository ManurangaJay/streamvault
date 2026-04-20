package com.streamvault.command_service.domain.event;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class AccountClosed extends BaseDomainEvent {
    private final String reason;
}
