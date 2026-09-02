package com.cart.service;

import com.cart.config.AppProperties;
import com.cart.messaging.CartEventProducer;
import com.cart.model.CartItem;
import com.cart.repository.CartRedisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRedisRepository repository;

    @Mock
    private CartEventProducer eventProducer;

    @Mock
    private AppProperties props;

    @InjectMocks
    private CartService cartService;

    private CartItem laptop;

    @BeforeEach
    void setUp() {
        laptop = CartItem.builder()
                .productId("p1").name("Laptop").unitPrice(new BigDecimal("1200")).quantity(1).build();
    }

    @Test
    void addItemRejectsNonPositiveQuantity() {
        laptop.setQuantity(0);
        assertThatThrownBy(() -> cartService.addItem("u1", laptop))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).saveItem(any(), any());
    }

    @Test
    void addItemRejectsNegativePrice() {
        laptop.setUnitPrice(new BigDecimal("-1"));
        assertThatThrownBy(() -> cartService.addItem("u1", laptop))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addItemMergesQuantityWhenProductAlreadyInCart() {
        when(repository.findItem("u1", "p1")).thenReturn(Optional.of(
                CartItem.builder().productId("p1").name("Laptop").unitPrice(new BigDecimal("1200")).quantity(2).build()));

        cartService.addItem("u1", laptop);

        ArgumentCaptor<CartItem> saved = ArgumentCaptor.forClass(CartItem.class);
        verify(repository).saveItem(eq("u1"), saved.capture());
        assertThat(saved.getValue().getQuantity()).isEqualTo(3);
    }

    @Test
    void updateQuantityThrowsWhenItemMissing() {
        when(repository.findItem("u1", "p1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> cartService.updateQuantity("u1", "p1", 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateQuantityRejectsNonPositive() {
        assertThatThrownBy(() -> cartService.updateQuantity("u1", "p1", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void calculateTotalSumsSubtotals() {
        when(repository.findAllItems("u1")).thenReturn(List.of(
                CartItem.builder().productId("p1").name("Laptop").unitPrice(new BigDecimal("1200")).quantity(1).build(),
                CartItem.builder().productId("p2").name("Mouse").unitPrice(new BigDecimal("25")).quantity(2).build()));

        assertThat(cartService.calculateTotal("u1")).isEqualByComparingTo(new BigDecimal("1250"));
    }

    @Test
    void checkoutOnEmptyCartThrowsAndDoesNotPublish() {
        when(repository.findAllItems("u1")).thenReturn(List.of());
        assertThatThrownBy(() -> cartService.checkout("u1"))
                .isInstanceOf(IllegalStateException.class);
        verify(eventProducer, never()).publishOrderCreated(any(), any());
        verify(repository, never()).clear(any());
    }

    @Test
    void checkoutWithDuplicateIdempotencyKeyDoesNotPublish() {
        when(props.getCheckout()).thenReturn(new AppProperties.Checkout());
        when(repository.tryStartCheckout(eq("key-1"), any())).thenReturn(false);

        cartService.checkout("u1", "key-1");

        verify(eventProducer, never()).publishOrderCreated(any(), any());
        verify(repository, never()).clear(any());
    }

    @Test
    void checkoutPublishesThenClears() {
        when(repository.findAllItems("u1")).thenReturn(List.of(laptop));

        cartService.checkout("u1");

        verify(eventProducer).publishOrderCreated("u1", List.of(laptop));
        verify(repository).clear("u1");
    }
}
