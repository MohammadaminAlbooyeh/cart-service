package com.cart;

import com.cart.model.CartItem;
import com.cart.service.CartService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = "cart.checkout")
@ActiveProfiles("dev")
class CartCheckoutContractTest {

    private static final long TIMEOUT_MS = 15_000;

    @Autowired
    private CartService cartService;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void checkoutPublishesCartCheckoutEvent() throws Exception {
        cartService.addItem("u-contract", CartItem.builder()
                .productId("p1").name("Laptop").unitPrice(new BigDecimal("1200")).quantity(1).build());
        cartService.addItem("u-contract", CartItem.builder()
                .productId("p2").name("Mouse").unitPrice(new BigDecimal("25")).quantity(2).build());

        cartService.checkout("u-contract");

        Map<String, Object> props = KafkaTestUtils.consumerProps("test-cart-checkout", "true", embeddedKafkaBroker);
        props.put("key.deserializer", org.apache.kafka.common.serialization.StringDeserializer.class);
        try (Consumer<String, String> consumer =
                     new DefaultKafkaConsumerFactory<String, String>(props).createConsumer()) {
            consumer.subscribe(List.of("cart.checkout"));
            ConsumerRecord<String, String> record =
                    KafkaTestUtils.getSingleRecord(consumer, "cart.checkout", java.time.Duration.ofMillis(TIMEOUT_MS));

            assertThat(record).isNotNull();
            assertThat(record.key()).isNotBlank();
            JsonNode event = objectMapper.readTree(record.value());
            assertThat(event.get("orderId").asText()).isEqualTo(record.key());
            assertThat(event.get("userId").asText()).isEqualTo("u-contract");
            assertThat(event.get("items")).hasSize(2);
            assertThat(event.get("totalAmount").decimalValue())
                    .isEqualByComparingTo(new BigDecimal("1250"));
            List<String> productIds = new java.util.ArrayList<>();
            event.get("items").forEach(i -> productIds.add(i.get("productId").asText()));
            assertThat(productIds).containsExactlyInAnyOrder("p1", "p2");
        }

        assertThat(cartService.getCart("u-contract")).isEmpty();
    }
}