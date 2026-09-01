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
import java.util.concurrent.TimeUnit;

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
            String json = objectMapper.writeValueAsString(payload);
            // Block on the broker ack so a failed publish surfaces before the caller clears the cart.
            kafkaTemplate.send(CHECKOUT_TOPIC, orderId, json).get(10, TimeUnit.SECONDS);
            log.info("Published {} event for order {} and user {}", CHECKOUT_TOPIC, orderId, userId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing checkout event", e);
        } catch (Exception e) {
            log.error("Failed to publish {} event for user {}", CHECKOUT_TOPIC, userId, e);
            throw new IllegalStateException("Could not publish checkout event", e);
        }
    }
}