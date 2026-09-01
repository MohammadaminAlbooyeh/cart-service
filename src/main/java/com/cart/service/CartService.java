package com.cart.service;

import com.cart.messaging.CartEventProducer;
import com.cart.model.CartItem;
import com.cart.repository.CartRedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRedisRepository cartRepository;
    private final CartEventProducer eventProducer;

    public List<CartItem> getCart(String userId) {
        return cartRepository.findAllItems(userId);
    }

    public void addItem(String userId, CartItem item) {
        if (item.getProductId() == null || item.getProductId().isBlank()) {
            throw new IllegalArgumentException("Product id is required");
        }
        if (item.getQuantity() == null || item.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (item.getUnitPrice() == null || item.getUnitPrice().signum() < 0) {
            throw new IllegalArgumentException("Unit price is required");
        }
        cartRepository.findItem(userId, item.getProductId())
                .ifPresent(existing -> item.setQuantity(existing.getQuantity() + item.getQuantity()));
        cartRepository.saveItem(userId, item);
    }

    public void updateQuantity(String userId, String productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        CartItem item = cartRepository.findItem(userId, productId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found in cart"));
        item.setQuantity(quantity);
        cartRepository.saveItem(userId, item);
    }

    public void removeItem(String userId, String productId) {
        cartRepository.deleteItem(userId, productId);
    }

    public void clearCart(String userId) {
        cartRepository.clear(userId);
    }

    public BigDecimal calculateTotal(String userId) {
        return getCart(userId).stream()
                .map(CartItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void checkout(String userId) {
        List<CartItem> items = getCart(userId);
        if (items.isEmpty()) {
            throw new IllegalStateException("Cart is empty");
        }
        eventProducer.publishOrderCreated(userId, items);
        cartRepository.clear(userId);
    }
}