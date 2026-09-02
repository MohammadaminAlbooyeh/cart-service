package com.cart.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaTopicConfigTest {

    @Test
    void cartCheckoutTopicHasCorrectNamePartitionsAndReplication() {
        KafkaTopicConfig config = new KafkaTopicConfig();
        NewTopic topic = config.cartCheckoutTopic();

        assertThat(topic.name()).isEqualTo("cart.checkout");
        assertThat(topic.numPartitions()).isEqualTo(1);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }
}
