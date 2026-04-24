package com.streamvault.command_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    private static final String RETENTION_7_DAYS = "604800000";

    @Bean
    public NewTopic transactionEventsTopic() {
        return TopicBuilder.name("transaction.events")
                .partitions(3)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, RETENTION_7_DAYS)
                .build();
    }

    @Bean
    public NewTopic accountEventsTopic() {
        return TopicBuilder.name("account.events")
                .partitions(3)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, RETENTION_7_DAYS)
                .build();
    }
}
