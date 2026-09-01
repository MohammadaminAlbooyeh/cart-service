package com.cart.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Jwt jwt = new Jwt();
    private final Cart cart = new Cart();
    private final Checkout checkout = new Checkout();
    private final Cors cors = new Cors();
    private final RateLimit ratelimit = new RateLimit();

    public Jwt getJwt() {
        return jwt;
    }

    public Cart getCart() {
        return cart;
    }

    public Checkout getCheckout() {
        return checkout;
    }

    public Cors getCors() {
        return cors;
    }

    public RateLimit getRatelimit() {
        return ratelimit;
    }

    public static class Jwt {
        /** Built-in development secret; rejected in production unless explicitly allowed. */
        public static final String DEV_SECRET = "dev-secret-change-me-in-production";

        private String secret = DEV_SECRET;
        private boolean allowInsecureSecret = true;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public boolean isAllowInsecureSecret() {
            return allowInsecureSecret;
        }

        public void setAllowInsecureSecret(boolean allowInsecureSecret) {
            this.allowInsecureSecret = allowInsecureSecret;
        }
    }

    public static class Cart {
        private Duration ttl;

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }
    }

    public static class Checkout {
        private Duration idempotencyTtl = Duration.ofHours(1);

        public Duration getIdempotencyTtl() {
            return idempotencyTtl;
        }

        public void setIdempotencyTtl(Duration idempotencyTtl) {
            this.idempotencyTtl = idempotencyTtl;
        }
    }

    public static class Cors {
        private List<String> allowedOrigins = List.of("*");
        private List<String> allowedMethods = List.of("GET", "POST", "PUT", "DELETE", "OPTIONS");

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }

        public List<String> getAllowedMethods() {
            return allowedMethods;
        }

        public void setAllowedMethods(List<String> allowedMethods) {
            this.allowedMethods = allowedMethods;
        }
    }

    public static class RateLimit {
        private boolean enabled = true;
        private int requestsPerMinute = 120;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getRequestsPerMinute() {
            return requestsPerMinute;
        }

        public void setRequestsPerMinute(int requestsPerMinute) {
            this.requestsPerMinute = requestsPerMinute;
        }
    }
}
