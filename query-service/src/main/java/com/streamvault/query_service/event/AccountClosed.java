package com.streamvault.query_service.event;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccountClosed extends BaseDomainEvent{
    private String reason;
}
