package com.cart.controller;

import com.cart.config.SecurityConfig;
import com.cart.service.CartService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CartController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("dev")
class CartControllerTest {

    private static final String SECRET = "dev-secret-change-me-in-production";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CartService cartService;

    @Test
    void addItemRejectsInvalidBody() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("name", "Laptop", "quantity", 0));

        mockMvc.perform(post("/api/v1/cart/items").header("Authorization", bearer("u1"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addItemAcceptsValidBody() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "productId", "p1", "name", "Laptop", "unitPrice", 1200, "quantity", 1));

        mockMvc.perform(post("/api/v1/cart/items").header("Authorization", bearer("u1"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        verify(cartService).addItem(eq("u1"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateQuantityRejectsMissingQuantity() throws Exception {
        mockMvc.perform(put("/api/v1/cart/items/p1").header("Authorization", bearer("u1"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void totalReturnsAmount() throws Exception {
        when(cartService.calculateTotal("u1")).thenReturn(new BigDecimal("1250"));

        mockMvc.perform(get("/api/v1/cart/total").header("Authorization", bearer("u1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(1250));
    }

    @Test
    void checkoutOnEmptyCartReturnsConflict() throws Exception {
        doThrow(new IllegalStateException("Cart is empty")).when(cartService).checkout("u1", null);

        mockMvc.perform(post("/api/v1/cart/checkout").header("Authorization", bearer("u1")))
                .andExpect(status().isConflict());
    }

    private String bearer(String subject) throws JOSEException {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(1, ChronoUnit.HOURS)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(SECRET.getBytes()));
        return "Bearer " + jwt.serialize();
    }
}
