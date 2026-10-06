package com.example.matchingengine.dto;

import java.util.List;

/**
 * Snapshot of an order book for a given symbol.
 *
 * <p>Bids are sorted descending by price, asks ascending.</p>
 */
public record OrderBookSnapshot(
        String symbol,
        List<PriceLevelEntry> bids,
        List<PriceLevelEntry> asks
) {

    /**
     * Aggregated view of a single price level.
     *
     * @param price         fixed-point price
     * @param totalQuantity sum of remaining quantities at this price
     * @param orderCount    number of orders at this price
     */
    public record PriceLevelEntry(long price, long totalQuantity, int orderCount) {}
}
