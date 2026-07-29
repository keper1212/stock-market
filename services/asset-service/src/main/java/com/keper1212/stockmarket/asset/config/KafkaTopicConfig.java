package com.keper1212.stockmarket.asset.config;

import com.keper1212.stockmarket.common.event.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    private static final int PARTITION_COUNT = 3;

    @Bean
    public NewTopic assetCommandsTopic() {
        return TopicBuilder.name(KafkaTopics.ASSET_COMMANDS)
                .partitions(PARTITION_COUNT)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic assetEventsTopic() {
        return TopicBuilder.name(KafkaTopics.ASSET_EVENTS)
                .partitions(PARTITION_COUNT)
                .replicas(1)
                .build();
    }
}
