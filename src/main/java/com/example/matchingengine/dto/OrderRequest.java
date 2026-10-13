package com.example.matchingengine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Inbound DTO for submitting an order via the REST API or Kafka topic.
 *
 * @param symbol   ticker symbol, e.g. {@code "AAPL"}
 * @param side     {@code "BUY"} or {@code "SELL"}
 * @param type     {@code "LIMIT"} or {@code "MARKET"}
 * @param price    fixed-point price (scale = 100, e.g. 150.25 → 15025). Ignored for MARKET orders.
 * @param quantity integer quantity (> 0)
 */
public record OrderRequest(
        @NotBlank(message = "Symbol is required")
        String symbol,

        @NotBlank(message = "Side is required")
        @Pattern(regexp = "(?i)^(BUY|SELL)$", message = "Side must be BUY or SELL")
        String side,

        @NotBlank(message = "Type is required")
        @Pattern(regexp = "(?i)^(LIMIT|MARKET|IOC|FOK)$", message = "Type must be LIMIT, MARKET, IOC, or FOK")
        String type,

        long price,

        @Positive(message = "Quantity must be strictly positive")
        long quantity
) {
    public OrderRequest {
        if (symbol != null) {
            symbol = symbol.trim().toUpperCase();
        }
        if (side != null) {
            side = side.trim().toUpperCase();
        }
        if (type != null) {
            type = type.trim().toUpperCase();
        }
    }
}
