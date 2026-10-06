package com.example.matchingengine.dto;

/**
 * DTO representing a single trade execution.
 */
public record TradeResponse(
        long tradeId,
        long buyOrderId,
        long sellOrderId,
        String symbol,
        long price,
        long quantity
) {}
