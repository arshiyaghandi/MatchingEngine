package com.example.matchingengine.api;

import com.example.matchingengine.exception.InvalidOrderException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Global exception handler that catches unhandled exceptions from the
 * reactive chain and returns structured JSON error responses using Jackson.
 */
@Component
@Order(-2)
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ObjectMapper objectMapper;

    public GlobalExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status;
        String message;

        if (ex instanceof InvalidOrderException || ex instanceof IllegalArgumentException) {
            status = HttpStatus.BAD_REQUEST;
            message = ex.getMessage();
        } else if (ex instanceof ResponseStatusException rse) {
            status = HttpStatus.valueOf(rse.getStatusCode().value());
            message = rse.getReason() != null ? rse.getReason() : ex.getMessage();
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            message = "Internal server error";
            log.error("Unhandled exception processing request: {}", exchange.getRequest().getURI(), ex);
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(Map.of("error", message != null ? message : "Unknown error"));
        } catch (JsonProcessingException e) {
            bytes = ("{\"error\":\"" + (message != null ? message.replace("\"", "\\\"") : "") + "\"}").getBytes();
        }

        var buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
