package com.example.matchingengine.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.web.reactive.function.server.RequestPredicates.*;

/**
 * Functional routing configuration for the REST API.
 *
 * <pre>
 * POST   /api/orders              → submit order
 * DELETE /api/orders              → cancel order
 * GET    /api/orderbook/{symbol}  → order-book snapshot
 * </pre>
 */
@Configuration
public class RouterConfig {

    @Bean
    public RouterFunction<ServerResponse> routes(MatchingEngineHandler handler) {
        return RouterFunctions
                .route(POST("/api/orders").and(accept(APPLICATION_JSON)), handler::submitOrder)
                .andRoute(DELETE("/api/orders").and(accept(APPLICATION_JSON)), handler::cancelOrder)
                .andRoute(GET("/api/orderbook/{symbol}"), handler::getOrderBook);
    }
}
