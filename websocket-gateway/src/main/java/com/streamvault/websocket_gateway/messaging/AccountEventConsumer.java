package com.streamvault.websocket_gateway.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

@Slf4j
@Configuration
public class AccountEventConsumer {

    @Bean
    public Consumer<Message<String>> accountEventsConsumer() {
        return message -> {
            String eventType = message.getHeaders().get("eventType", String.class);
            String payload = message.getPayload();

            log.info("Received event type [{}] from account.events topic", eventType);
        };
    }
}
