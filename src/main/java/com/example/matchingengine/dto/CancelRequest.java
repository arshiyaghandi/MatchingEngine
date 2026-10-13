package com.example.matchingengine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Inbound DTO for cancelling an order.
 */
public record CancelRequest(
        @NotBlank(message = "Symbol is required")
        String symbol,

        @Positive(message = "OrderId must be positive")
        long orderId,

        @NotBlank(message = "Side is required")
        @Pattern(regexp = "(?i)^(BUY|SELL)$", message = "Side must be BUY or SELL")
        String side
) {
    public CancelRequest {
        if (symbol != null) {
            symbol = symbol.trim().toUpperCase();
        }
        if (side != null) {
            side = side.trim().toUpperCase();
        }
    }
}
