package com.cart.repository;

import com.cart.model.CartItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class CartRedisRepositoryTest {

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withCommand("redis-server", "--save", "", "--appendonly", "no");

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () ->
                String.valueOf(redis.getMappedPort(6379)));
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
    }

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CartRedisRepository repository;

    private static final String USER_ID = "repo-test-user";

    @BeforeEach
    void setUp() {
        redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            connection.flushAll();
            return null;
        });
    }
    void saveAndFindItem() {
        CartItem item = CartItem.builder()
                .productId("p1").name("Laptop").unitPrice(new BigDecimal("1200")).quantity(2).build();
        repository.saveItem(USER_ID, item);

        Optional<CartItem> found = repository.findItem(USER_ID, "p1");
        assertThat(found).isPresent();
        assertThat(found.get().getProductId()).isEqualTo("p1");
        assertThat(found.get().getQuantity()).isEqualTo(2);
    }

    @Test
    void findAllItemsReturnsAll() {
        repository.saveItem(USER_ID, CartItem.builder()
                .productId("p1").name("Laptop").unitPrice(new BigDecimal("1200")).quantity(1).build());
        repository.saveItem(USER_ID, CartItem.builder()
                .productId("p2").name("Mouse").unitPrice(new BigDecimal("25")).quantity(3).build());

        assertThat(repository.findAllItems(USER_ID)).hasSize(2);
    }

    @Test
    void findAllItemsReturnsEmptyWhenNoCart() {
        assertThat(repository.findAllItems("non-existent-user")).isEmpty();
    }

    @Test
    void deleteItemRemovesItem() {
        repository.saveItem(USER_ID, CartItem.builder()
                .productId("p1").name("Laptop").unitPrice(new BigDecimal("1200")).quantity(1).build());

        repository.deleteItem(USER_ID, "p1");

        assertThat(repository.findItem(USER_ID, "p1")).isEmpty();
        assertThat(repository.countItems(USER_ID)).isZero();
    }

    @Test
    void clearDeletesEntireCart() {
        repository.saveItem(USER_ID, CartItem.builder()
                .productId("p1").name("Laptop").unitPrice(new BigDecimal("1200")).quantity(1).build());
        repository.saveItem(USER_ID, CartItem.builder()
                .productId("p2").name("Mouse").unitPrice(new BigDecimal("25")).quantity(2).build());

        repository.clear(USER_ID);

        assertThat(repository.findAllItems(USER_ID)).isEmpty();
        assertThat(repository.countItems(USER_ID)).isZero();
    }

    @Test
    void findItemReturnsEmptyWhenNotPresent() {
        assertThat(repository.findItem(USER_ID, "missing")).isEmpty();
    }

    @Test
    void tryStartCheckoutIsIdempotent() {
        Duration ttl = Duration.ofMinutes(5);

        boolean first = repository.tryStartCheckout("idem-key-1", ttl);
        boolean second = repository.tryStartCheckout("idem-key-1", ttl);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
    }

    @Test
    void tryStartCheckoutAllowsDifferentKeys() {
        Duration ttl = Duration.ofMinutes(5);

        boolean first = repository.tryStartCheckout("key-a", ttl);
        boolean second = repository.tryStartCheckout("key-b", ttl);

        assertThat(first).isTrue();
        assertThat(second).isTrue();
    }

    @Test
    void saveItemOverwritesExistingProduct() {
        CartItem original = CartItem.builder()
                .productId("p1").name("Laptop").unitPrice(new BigDecimal("1200")).quantity(1).build();
        repository.saveItem(USER_ID, original);

        CartItem updated = CartItem.builder()
                .productId("p1").name("Laptop Pro").unitPrice(new BigDecimal("1500")).quantity(3).build();
        repository.saveItem(USER_ID, updated);

        Optional<CartItem> found = repository.findItem(USER_ID, "p1");
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Laptop Pro");
        assertThat(found.get().getQuantity()).isEqualTo(3);
    }
}
