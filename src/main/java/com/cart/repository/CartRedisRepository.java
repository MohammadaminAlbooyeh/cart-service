package com.cart.repository;

import com.cart.model.CartItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class CartRedisRepository {

    private static final String CART_KEY_PREFIX = "cart:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.cart.ttl:}")
    private String cartTtlRaw;

    private String keyFor(String userId) {
        return CART_KEY_PREFIX + userId;
    }

    private Duration cartTtl() {
        if (cartTtlRaw == null || cartTtlRaw.isBlank()) {
            return null;
        }
        return Duration.parse(cartTtlRaw.trim());
    }

    private void applyTtl(String userId) {
        Duration ttl = cartTtl();
        if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
            redisTemplate.expire(keyFor(userId), ttl);
        }
    }

    public void saveItem(String userId, CartItem item) {
        try {
            HashOperations<String, String, String> ops = redisTemplate.opsForHash();
            ops.put(keyFor(userId), item.getProductId(), objectMapper.writeValueAsString(item));
            applyTtl(userId);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize cart item", e);
        }
    }

    public Optional<CartItem> findItem(String userId, String productId) {
        HashOperations<String, String, String> ops = redisTemplate.opsForHash();
        String raw = ops.get(keyFor(userId), productId);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(raw, CartItem.class));
        } catch (Exception e) {
            throw new IllegalStateException("Could not deserialize cart item", e);
        }
    }

    public List<CartItem> findAllItems(String userId) {
        HashOperations<String, String, String> ops = redisTemplate.opsForHash();
        Map<String, String> entries = ops.entries(keyFor(userId));
        if (entries == null || entries.isEmpty()) {
            return Collections.emptyList();
        }
        return entries.values().stream()
                .map(raw -> {
                    try {
                        return objectMapper.readValue(raw, CartItem.class);
                    } catch (Exception e) {
                        throw new IllegalStateException("Could not deserialize cart item", e);
                    }
                })
                .collect(Collectors.toList());
    }

    public void deleteItem(String userId, String productId) {
        redisTemplate.opsForHash().delete(keyFor(userId), productId);
    }

    public void clear(String userId) {
        redisTemplate.delete(keyFor(userId));
    }

    public long countItems(String userId) {
        return redisTemplate.opsForHash().size(keyFor(userId));
    }
}
