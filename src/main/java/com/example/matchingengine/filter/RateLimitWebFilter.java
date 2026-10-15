package com.example.matchingengine.filter;

import com.example.matchingengine.config.RateLimiterConfig;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * WebFilter that enforces per-IP rate limiting on order submission endpoints.
 *
 * <p>Returns HTTP 429 (Too Many Requests) when the client exceeds the
 * configured request rate. Only applies to {@code /api/orders} endpoints.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitWebFilter implements WebFilter {

    private final RateLimiterConfig rateLimiter;

    public RateLimitWebFilter(RateLimiterConfig rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Only rate-limit order submission endpoints
        if (!path.startsWith("/api/orders")) {
            return chain.filter(exchange);
        }

        InetSocketAddress remoteAddr = exchange.getRequest().getRemoteAddress();
        String clientIp = (remoteAddr != null && remoteAddr.getAddress() != null)
                ? remoteAddr.getAddress().getHostAddress()
                : "unknown";

        if (!rateLimiter.isAllowed(clientIp)) {
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }
}
