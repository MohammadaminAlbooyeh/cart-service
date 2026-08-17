package com.cart.messaging;

import com.cart.model.CartItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CartEventProducer {

    public static final String CHECKOUT_TOPIC = "cart.checkout";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishOrderCreated(String userId, List<CartItem> items) {
        String orderId = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of(
                "orderId", orderId,
                "userId", userId,
                "items", items,
                "totalAmount", items.stream()
                        .map(CartItem::getSubtotal)
                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
        );
        try {
            kafkaTemplate.send(CHECKOUT_TOPIC, orderId, objectMapper.writeValueAsString(payload));
            log.info("Published {} event for order {} and user {}", CHECKOUT_TOPIC, orderId, userId);
        } catch (Exception e) {
            log.error("Failed to publish {} event for user {}", CHECKOUT_TOPIC, userId, e);
            throw new IllegalStateException("Could not publish checkout event", e);
        }
    }
}