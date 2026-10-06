package com.example.matchingengine.engine;

import com.example.matchingengine.domain.Order;
import com.example.matchingengine.domain.Side;
import com.example.matchingengine.domain.Trade;
import com.example.matchingengine.exception.InvalidOrderException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core matching engine managing multiple {@link OrderBook}s (one per symbol).
 *
 * <p><strong>Thread-safety:</strong> Concurrent submissions for <em>different</em>
 * symbols proceed in parallel. Submissions for the <em>same</em> symbol are
 * serialised via a per-{@code OrderBook} intrinsic lock.</p>
 *
 * <p>This class has <strong>no Spring dependencies</strong> and can be tested
 * without an application context.</p>
 */
public class MatchingEngine {

    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();
    private final Set<Long> knownOrderIds = ConcurrentHashMap.newKeySet();

    /**
     * Submits an order for matching.
     *
     * @param order the order to process
     * @return trades generated from matching (may be empty)
     * @throws InvalidOrderException if the order ID is a duplicate
     */
    public List<Trade> submitOrder(Order order) {
        if (!knownOrderIds.add(order.getId())) {
            throw new InvalidOrderException("Duplicate order ID: " + order.getId());
        }

        OrderBook book = orderBooks.computeIfAbsent(order.getSymbol(), OrderBook::new);
        synchronized (book) {
            return book.addOrder(order);
        }
    }

    /**
     * Cancels an existing resting order.
     *
     * @param symbol instrument symbol
     * @param orderId order to cancel
     * @param side    expected side of the order
     * @return true if the order was found and cancelled
     */
    public boolean cancelOrder(String symbol, long orderId, Side side) {
        OrderBook book = orderBooks.get(symbol);
        if (book == null) return false;

        synchronized (book) {
            boolean cancelled = book.cancelOrder(orderId, side);
            if (cancelled) {
                knownOrderIds.remove(orderId);
            }
            return cancelled;
        }
    }

    /** Returns the order book for a symbol, or null if none exists. */
    public OrderBook getOrderBook(String symbol) {
        return orderBooks.get(symbol);
    }

    /** Checks whether an order ID has been seen by this engine. */
    public boolean hasOrder(long orderId) {
        return knownOrderIds.contains(orderId);
    }
}
