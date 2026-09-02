package com.cart.security;

import com.cart.config.SecurityConfig;
import com.cart.controller.CartController;
import com.cart.service.CartService;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CartController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("dev")
class CartControllerSecurityTest {

    private static final String SECRET = "dev-secret-change-me-in-production";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CartService cartService;

    @Test
    void rejectsRequestWithNoToken() throws Exception {
        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsRequestWithInvalidSignature() throws Exception {
        String token = signedToken("u-1", "wrong-secret-wrong-secret-wrong-secret");

        mockMvc.perform(get("/api/v1/cart").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        String token = expiredToken("u-1");

        mockMvc.perform(get("/api/v1/cart").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsValidTokenAndUsesSubjectAsUserId() throws Exception {
        when(cartService.getCart("u-42")).thenReturn(List.of());
        String token = signedToken("u-42", SECRET);

        mockMvc.perform(get("/api/v1/cart").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private String signedToken(String subject, String secret) throws JOSEException {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(1, ChronoUnit.HOURS)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    private String expiredToken(String subject) throws JOSEException {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issueTime(Date.from(now.minus(2, ChronoUnit.HOURS)))
                .expirationTime(Date.from(now.minus(1, ChronoUnit.HOURS)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
