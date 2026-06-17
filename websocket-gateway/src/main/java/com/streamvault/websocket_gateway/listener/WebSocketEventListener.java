package com.streamvault.websocket_gateway.listener;

import com.streamvault.websocket_gateway.sercurity.JwtService;
import com.streamvault.websocket_gateway.service.SessionRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final SessionRegistryService sessionRegistry;
     private final JwtService jwtService;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        String authHeader = accessor.getFirstNativeHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            try {
                // Validate token and extract the USER ID
                 String userId = jwtService.extractUserId(token);

                // Store the userId in the WebSocket session attributes for future reference
                if (accessor.getSessionAttributes() != null) {
                    accessor.getSessionAttributes().put("userId", userId);
                }

                log.info("WS connection authenticated for User [{}] on Session [{}]", userId, sessionId);
            } catch (Exception e) {
                log.error("Failed to authenticate STOMP connection. Session: {}", sessionId);
                throw new IllegalArgumentException("Invalid JWT Token");
            }
        } else {
            log.warn("STOMP connection attempted without Authorization header. Session: {}", sessionId);
            throw new IllegalArgumentException("Missing Authorization header");
        }
    }

    @EventListener
    public void handleSubscribeEvent(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        String destination = accessor.getDestination();

        // Retrieve the authenticated user ID we saved during the connect phase
        String userId = (String) accessor.getSessionAttributes().get("userId");

        if (destination != null && destination.startsWith("/topic/accounts/")) {
            // Extract the accountId from the destination path
            String[] parts = destination.split("/");
            if (parts.length >= 4) {
                String accountId = parts[3];
                sessionRegistry.registerSession(accountId, sessionId);
            }
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();

        if (sessionId != null) {
            sessionRegistry.unregisterSession(sessionId);
        }
    }
}