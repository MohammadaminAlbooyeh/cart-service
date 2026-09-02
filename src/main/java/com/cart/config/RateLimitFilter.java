package com.cart.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight in-memory fixed-window rate limiter, keyed by authenticated user
 * (falling back to client IP). Good enough for a single instance; a distributed
 * deployment should move the counter into Redis.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {

    private final boolean enabled;
    private final int limit;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(AppProperties props) {
        this.enabled = props.getRatelimit().isEnabled();
        this.limit = props.getRatelimit().getRequestsPerMinute();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !enabled
                || path.startsWith("/actuator")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String key = clientKey(request);
        long minute = Instant.now().getEpochSecond() / 60;
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || existing.minute != minute) {
                return new Window(minute);
            }
            return existing;
        });

        int count = window.count.incrementAndGet();
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, limit - count)));

        if (count > limit) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Rate limit exceeded\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String clientKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return "user:" + auth.getName();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = (forwarded != null && !forwarded.isBlank())
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
        return "ip:" + ip;
    }

    private static final class Window {
        private final long minute;
        private final AtomicInteger count = new AtomicInteger(0);

        private Window(long minute) {
            this.minute = minute;
        }
    }
}
