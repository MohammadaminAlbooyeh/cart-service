package com.cart.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    private AppProperties props;
    private RateLimitFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() throws Exception {
        props = new AppProperties();
        props.getRatelimit().setEnabled(true);
        props.getRatelimit().setRequestsPerMinute(5);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);

        when(request.getRequestURI()).thenReturn("/api/v1/cart");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        SecurityContextHolder.clearContext();

        filter = new RateLimitFilter(props);
    }

    @Test
    void allowsRequestsUnderLimit() throws ServletException, IOException {
        for (int i = 0; i < 5; i++) {
            filter.doFilterInternal(request, response, chain);
        }
        verify(chain, times(5)).doFilter(request, response);
    }

    @Test
    void blocksRequestOverLimitWith429() throws ServletException, IOException {
        for (int i = 0; i < 5; i++) {
            filter.doFilterInternal(request, response, chain);
        }

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(429);
        verify(chain, times(5)).doFilter(request, response);
    }

    @Test
    void setsRateLimitHeaders() throws ServletException, IOException {
        filter.doFilterInternal(request, response, chain);

        verify(response).setHeader("X-RateLimit-Limit", "5");
        verify(response).setHeader("X-RateLimit-Remaining", "4");
    }

    @Test
    void skipsFilterWhenDisabled() {
        props.getRatelimit().setEnabled(false);
        filter = new RateLimitFilter(props);

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void skipsFilterForActuatorPath() {
        when(request.getRequestURI()).thenReturn("/actuator/health");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void skipsFilterForSwaggerPath() {
        when(request.getRequestURI()).thenReturn("/swagger-ui.html");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void skipsFilterForApiDocsPath() {
        when(request.getRequestURI()).thenReturn("/v3/api-docs");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void doesNotSkipForCartPath() {
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void eachClientIpHasSeparateLimit() throws ServletException, IOException {
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        for (int i = 0; i < 5; i++) {
            filter.doFilterInternal(request, response, chain);
        }
        verify(chain, times(5)).doFilter(request, response);

        when(request.getRemoteAddr()).thenReturn("10.0.0.2");
        for (int i = 0; i < 5; i++) {
            filter.doFilterInternal(request, response, chain);
        }
        verify(chain, times(10)).doFilter(request, response);
    }

    @Test
    void forwardedIpUsedForClientKey() throws ServletException, IOException {
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5, 10.0.0.1");
        filter.doFilterInternal(request, response, chain);
        verify(chain).doFilter(request, response);
    }
}
