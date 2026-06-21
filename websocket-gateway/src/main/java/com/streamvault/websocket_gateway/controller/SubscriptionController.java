package com.streamvault.websocket_gateway.controller;

import com.streamvault.websocket_gateway.service.SessionRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class SubscriptionController {

    private final SessionRegistryService sessionRegistry;

    @MessageMapping
    public void handleSubscriptionRequest(@DestinationVariable String accountId, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();

        String userId = (String) accessor.getSessionAttributes().get("userId");

        log.info("User [{}] requesting subscription to Account [{}] on Session [{}]", userId, accountId, sessionId);

        sessionRegistry.registerSession(accountId, sessionId);
    }
}
