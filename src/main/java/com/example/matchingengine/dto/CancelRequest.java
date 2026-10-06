package com.example.matchingengine.dto;

/**
 * Inbound DTO for cancelling an order.
 */
public record CancelRequest(
        String symbol,
        long orderId,
        String side
) {}
