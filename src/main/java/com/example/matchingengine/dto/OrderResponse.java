package com.example.matchingengine.dto;

import java.util.List;

/**
 * Response DTO returned after submitting an order.
 *
 * @param orderId  assigned order ID
 * @param symbol   ticker symbol
 * @param side     BUY or SELL
 * @param price    submitted price (fixed-point)
 * @param quantity submitted quantity
 * @param trades   list of trades that occurred immediately
 */
public record OrderResponse(
        long orderId,
        String symbol,
        String side,
        long price,
        long quantity,
        List<TradeResponse> trades
) {}
