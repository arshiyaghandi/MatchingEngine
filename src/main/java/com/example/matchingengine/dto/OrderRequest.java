package com.example.matchingengine.dto;

/**
 * Inbound DTO for submitting an order via the REST API.
 *
 * @param symbol   ticker symbol, e.g. {@code "AAPL"}
 * @param side     {@code "BUY"} or {@code "SELL"}
 * @param type     {@code "LIMIT"} (only supported type for now)
 * @param price    fixed-point price (scale = 100, e.g. 150.25 → 15025)
 * @param quantity integer quantity
 */
public record OrderRequest(
        String symbol,
        String side,
        String type,
        long price,
        long quantity
) {}
