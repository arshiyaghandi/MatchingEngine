package com.example.matchingengine.filter;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reactive WebFilter that assigns a unique Correlation ID (Request ID) to each incoming HTTP request.
 *
 * <p>Inserts the {@code X-Correlation-ID} header into the response and propagates
 * it through Reactor Context for reactive tracing across thread hops.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdWebFilter implements WebFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String CORRELATION_ID_KEY = "correlationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        final String finalCorrelationId = correlationId;

        // Add to HTTP response headers
        exchange.getResponse().getHeaders().add(CORRELATION_ID_HEADER, finalCorrelationId);

        return chain.filter(exchange)
                .contextWrite(ctx -> ctx.put(CORRELATION_ID_KEY, finalCorrelationId))
                .doOnEach(signal -> {
                    if (signal.isOnNext() || signal.isOnError()) {
                        MDC.put(CORRELATION_ID_KEY, finalCorrelationId);
                    }
                })
                .doFinally(sig -> MDC.remove(CORRELATION_ID_KEY));
    }
}
