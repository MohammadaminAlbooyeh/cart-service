package com.cart.util;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Component
public class JwtTokenUtil {

    private final String secret;
    private final MACSigner signer;
    private final MACVerifier verifier;

    public JwtTokenUtil(@Value("${spring.security.oauth2.resource-server.jwt.secret}") String secret) {
        this.secret = secret;
        try {
            this.signer = new MACSigner(secret.getBytes());
            this.verifier = new MACVerifier(secret);
        } catch (JOSEException e) {
            throw new IllegalStateException("Invalid JWT secret", e);
        }
    }

    public String generateToken(String userId) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(userId)
                    .issuer("cart-service")
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(1, ChronoUnit.HOURS)))
                    .claim("user_id", userId)
                    .build();

            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HMAC_SHA256), claims);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not generate JWT token", e);
        }
    }

    public boolean validateToken(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            return jwt.verify(verifier) && !isExpired(jwt);
        } catch (Exception e) {
            return false;
        }
    }

    public String getUserIdFromToken(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            return jwt.getJWTClaimsSet().getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isExpired(SignedJWT jwt) {
        try {
            Date expiration = jwt.getJWTClaimsSet().getExpirationTime();
            return expiration != null && expiration.before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
}
