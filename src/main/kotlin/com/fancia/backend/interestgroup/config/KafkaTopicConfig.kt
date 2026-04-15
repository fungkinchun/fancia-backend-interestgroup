package com.fancia.backend.interestgroup.config

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

@Configuration
class KafkaTopicConfig {
    @Bean
    fun interestGroupsTopic(): NewTopic {
        return TopicBuilder.name("interestGroupsTopic")
            .partitions(3)
            .replicas(1)
            .build()
    }
}