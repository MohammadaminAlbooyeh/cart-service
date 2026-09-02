package com.cart.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.cart.messaging.CartEventProducer;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic cartCheckoutTopic() {
        return new NewTopic(CartEventProducer.CHECKOUT_TOPIC, 1, (short) 1);
    }
}
