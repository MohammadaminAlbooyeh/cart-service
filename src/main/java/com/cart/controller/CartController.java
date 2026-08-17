package com.cart.controller;

import com.cart.model.CartItem;
import com.cart.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public List<CartItem> getCart(@RequestHeader("X-User-Id") String userId) {
        return cartService.getCart(userId);
    }

    @PostMapping("/items")
    public ResponseEntity<CartItem> addItem(@RequestHeader("X-User-Id") String userId,
                                            @Valid @RequestBody CartItem item) {
        cartService.addItem(userId, item);
        return ResponseEntity.status(HttpStatus.CREATED).body(item);
    }

    @PutMapping("/items/{productId}")
    public ResponseEntity<Void> updateQuantity(@RequestHeader("X-User-Id") String userId,
                                               @PathVariable String productId,
                                               @RequestBody Map<String, Integer> body) {
        Integer quantity = body.get("quantity");
        if (quantity == null) {
            return ResponseEntity.badRequest().build();
        }
        cartService.updateQuantity(userId, productId, quantity);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<Void> removeItem(@RequestHeader("X-User-Id") String userId,
                                           @PathVariable String productId) {
        cartService.removeItem(userId, productId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(@RequestHeader("X-User-Id") String userId) {
        cartService.clearCart(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/total")
    public Map<String, BigDecimal> total(@RequestHeader("X-User-Id") String userId) {
        return Map.of("totalAmount", cartService.calculateTotal(userId));
    }

    @PostMapping("/checkout")
    public ResponseEntity<Void> checkout(@RequestHeader("X-User-Id") String userId) {
        cartService.checkout(userId);
        return ResponseEntity.accepted().build();
    }
}