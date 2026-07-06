package com.streamvault.websocket_gateway.messaging;

public record AlertNotificationDTO(
        String alertId,
        String type,
        String  message,
        String severity,
        String timestamp
) {
}
