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
import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public List<CartItem> getCart(Principal principal) {
        return cartService.getCart(principal.getName());
    }

    @PostMapping("/items")
    public ResponseEntity<CartItem> addItem(Principal principal,
                                            @Valid @RequestBody CartItem item) {
        cartService.addItem(principal.getName(), item);
        return ResponseEntity.status(HttpStatus.CREATED).body(item);
    }

    @PutMapping("/items/{productId}")
    public ResponseEntity<Void> updateQuantity(Principal principal,
                                               @PathVariable String productId,
                                               @RequestBody Map<String, Integer> body) {
        Integer quantity = body.get("quantity");
        if (quantity == null) {
            return ResponseEntity.badRequest().build();
        }
        cartService.updateQuantity(principal.getName(), productId, quantity);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<Void> removeItem(Principal principal,
                                           @PathVariable String productId) {
        cartService.removeItem(principal.getName(), productId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(Principal principal) {
        cartService.clearCart(principal.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/total")
    public Map<String, BigDecimal> total(Principal principal) {
        return Map.of("totalAmount", cartService.calculateTotal(principal.getName()));
    }

    @PostMapping("/checkout")
    public ResponseEntity<Void> checkout(Principal principal) {
        cartService.checkout(principal.getName());
        return ResponseEntity.accepted().build();
    }
}