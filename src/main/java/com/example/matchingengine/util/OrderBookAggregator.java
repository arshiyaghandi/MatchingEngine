package com.example.matchingengine.util;

import com.example.matchingengine.domain.Order;
import com.example.matchingengine.domain.PriceLevel;
import com.example.matchingengine.dto.OrderBookSnapshot;
import com.example.matchingengine.dto.OrderBookSnapshot.PriceLevelEntry;
import com.example.matchingengine.engine.OrderBook;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Stateless utility that converts an {@link OrderBook} into an
 * {@link OrderBookSnapshot} DTO.
 *
 * <p>Extracted to a single location to eliminate the duplicated
 * aggregation logic that previously lived in both the REST handler
 * and the Redis cache service.</p>
 */
public final class OrderBookAggregator {

    private OrderBookAggregator() { /* utility class */ }

    /**
     * Builds a complete snapshot of both sides of the book.
     *
     * <p><strong>Caller must hold the book lock</strong> when the book
     * is shared across threads.</p>
     *
     * @param symbol the instrument symbol
     * @param book   the order book to aggregate
     * @return an immutable snapshot
     */
    public static OrderBookSnapshot buildSnapshot(String symbol, OrderBook book) {
        List<PriceLevelEntry> bids = aggregateSide(book.getBids());
        List<PriceLevelEntry> asks = aggregateSide(book.getAsks());
        return new OrderBookSnapshot(symbol, bids, asks);
    }

    /**
     * Aggregates one side of the book into a list of price-level entries.
     *
     * @param side the bid or ask side (TreeMap of price → PriceLevel)
     * @return aggregated entries preserving the TreeMap's iteration order
     */
    public static List<PriceLevelEntry> aggregateSide(TreeMap<Long, PriceLevel> side) {
        List<PriceLevelEntry> entries = new ArrayList<>(side.size());
        for (Map.Entry<Long, PriceLevel> e : side.entrySet()) {
            long totalQty = 0;
            int count = 0;
            for (Order order : e.getValue().getOrders()) {
                totalQty += order.getRemainingQuantity();
                count++;
            }
            entries.add(new PriceLevelEntry(e.getKey(), totalQty, count));
        }
        return entries;
    }
}
