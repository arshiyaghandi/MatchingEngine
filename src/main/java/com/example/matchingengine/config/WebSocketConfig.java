package com.example.matchingengine.config;

import com.example.matchingengine.api.MarketDataWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.Map;

/**
 * WebSocket infrastructure configuration.
 *
 * <p>Registers the market-data WebSocket handler at {@code /ws/market}
 * and provides the adapter required by Spring WebFlux to handle
 * WebSocket upgrade requests.</p>
 */
@Configuration
public class WebSocketConfig {

    @Bean
    public HandlerMapping webSocketMapping(MarketDataWebSocketHandler handler) {
        var mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(Map.of("/ws/market", handler));
        mapping.setOrder(-1); // Higher priority than HTTP routes
        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
