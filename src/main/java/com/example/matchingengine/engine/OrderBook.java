package com.example.matchingengine.engine;

import com.example.matchingengine.domain.Order;
import com.example.matchingengine.domain.Side;
import com.example.matchingengine.domain.Trade;

import java.util.*;

/**
 * OrderBook manages the BID and ASK sides of the book for a specific symbol.
 * BIDs are ordered descending by price (highest price first).
 * ASKs are ordered ascending by price (lowest price first).
 */
public record OrderBook(String symbol) {
    // TODO: Choose appropriate data structures for BID and ASK to maintain price-time priority.
    // e.g., TreeMap could be used:
    // - Bids require reverse ordering (Collections.reverseOrder()) -> Highest price first.
    // - Asks require natural ordering -> Lowest price first.
    // Note: Do NOT use floating point numbers for prices.

    // TODO: Initialize data structures for bids and asks

    /**
     * Adds an order to the order book.
     * <p>
     * Expected Matching Rules:
     * - A BUY order can match with a SELL order when: buyPrice >= sellPrice
     * - A SELL order can match with a BUY order when: sellPrice <= buyPrice
     * - Execution price should be determined by the existing resting order according to the engine's price-time priority model.
     * - Orders at the same price level must maintain FIFO ordering.
     *
     * @param order the order to add
     * @return a list of trades generated from this order (empty if no match)
     */
    public List<Trade> addOrder(Order order) {
        // TODO: Implement the matching algorithm here.
        // 1. Check if the order can match with the opposite side (cross the spread).
        // 2. If it can, generate Trade objects, reduce quantities of both orders.
        //    * Use the resting order's price as the execution price.
        // 3. Remove fully filled orders from the book.
        // 4. If the incoming order has remaining quantity after matching all possible resting orders, 
        //    add the remainder to the corresponding side of the book (in a PriceLevel).

        return new ArrayList<>(); // Return actual trades once implemented
    }

    /**
     * Cancels an order in the book.
     *
     * @param orderId the ID of the order to cancel
     * @param side    the side of the order
     * @return true if order was successfully cancelled, false otherwise
     */
    public boolean cancelOrder(long orderId, Side side) {
        // TODO: Implement cancellation logic.
        // 1. Find the order by its ID.
        // 2. Remove it from its PriceLevel.
        // 3. If the PriceLevel becomes empty, remove the PriceLevel itself to avoid memory leaks.
        return false;
    }

    // TODO: Add methods to inspect the state of the book (e.g., getBestBid, getBestAsk) for tests.
}
