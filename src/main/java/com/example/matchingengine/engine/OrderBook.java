package com.example.matchingengine.engine;

import com.example.matchingengine.domain.Order;
import com.example.matchingengine.domain.OrderType;
import com.example.matchingengine.domain.PriceLevel;
import com.example.matchingengine.domain.Side;
import com.example.matchingengine.domain.Trade;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * OrderBook manages the BID and ASK sides for a single symbol.
 *
 * <h3>Price-Time Priority</h3>
 * <ul>
 *   <li>BIDs ordered <strong>descending</strong> by price (highest first)</li>
 *   <li>ASKs ordered <strong>ascending</strong>  by price (lowest first)</li>
 *   <li>Same price → FIFO (first order in, first matched)</li>
 * </ul>
 *
 * <h3>Matching Rules</h3>
 * <ul>
 *   <li>BUY crosses when {@code buyPrice >= bestAskPrice}</li>
 *   <li>SELL crosses when {@code sellPrice <= bestBidPrice}</li>
 *   <li>Execution price = <strong>resting</strong> order's price</li>
 * </ul>
 *
 * <h3>MARKET Orders</h3>
 * <p>MARKET orders match against any resting order (sentinel prices guarantee
 * a price-cross). Unfilled MARKET orders are <em>not</em> placed in the book.</p>
 *
 * <p><strong>Thread-safety:</strong> This class is <em>not</em> thread-safe.
 * External synchronization (e.g. per-symbol lock) is required.</p>
 */
public class OrderBook {

    private final String symbol;

    /** Bids: highest price first (reverse natural order). */
    private final TreeMap<Long, PriceLevel> bids;

    /** Asks: lowest price first (natural order). */
    private final TreeMap<Long, PriceLevel> asks;

    /** Fast O(1) lookup by order ID — used for cancellation. */
    private final Map<Long, Order> orderIndex;

    /** Trade-ID generator. */
    private final java.util.function.LongSupplier tradeIdSupplier;

    public OrderBook(String symbol) {
        this(symbol, new AtomicLong(1)::getAndIncrement);
    }

    public OrderBook(String symbol, java.util.function.LongSupplier tradeIdSupplier) {
        this.symbol = symbol;
        this.bids = new TreeMap<>(Collections.reverseOrder());
        this.asks = new TreeMap<>();
        this.orderIndex = new HashMap<>();
        this.tradeIdSupplier = (tradeIdSupplier != null) ? tradeIdSupplier : new AtomicLong(1)::getAndIncrement;
    }

    public String getSymbol() {
        return symbol;
    }

    // ────────────────────────────── Order Entry ──────────────────────────────

    /**
     * Adds an incoming order: first tries to match against the opposite side,
     * then rests any unfilled remainder in the book.
     *
     * <p>MARKET orders never rest — unfilled quantity is implicitly cancelled.</p>
     *
     * @param order the incoming order (aggressor)
     * @return trades generated (may be empty)
     */
    public List<Trade> addOrder(Order order) {
        // FOK (Fill-Or-Kill): check if full quantity can be matched immediately
        if (order.getType() == OrderType.FOK) {
            long available = getAvailableMatchingQuantity(order);
            if (available < order.getOriginalQuantity()) {
                // Cannot fill completely — kill the order immediately without any partial match
                return Collections.emptyList();
            }
        }

        List<Trade> trades = new ArrayList<>();

        if (order.getSide() == Side.BUY) {
            matchAgainst(order, asks, trades, true);
        } else {
            matchAgainst(order, bids, trades, false);
        }

        // MARKET, IOC, and FOK orders NEVER rest in the book
        boolean canRestInBook = (order.getType() == OrderType.LIMIT);
        if (!order.isFilled() && canRestInBook) {
            insertIntoBook(order);
        }

        return trades;
    }

    /**
     * Calculates the total quantity available to match against this order
     * at prices that satisfy the price-cross condition (used for FOK pre-flight).
     */
    private long getAvailableMatchingQuantity(Order order) {
        TreeMap<Long, PriceLevel> oppositeSide = (order.getSide() == Side.BUY) ? asks : bids;
        long totalAvailable = 0;

        for (Map.Entry<Long, PriceLevel> entry : oppositeSide.entrySet()) {
            long restingPrice = entry.getKey();
            if (order.getSide() == Side.BUY && order.getPrice() < restingPrice) break;
            if (order.getSide() == Side.SELL && order.getPrice() > restingPrice) break;

            for (Order resting : entry.getValue().getOrders()) {
                totalAvailable += resting.getRemainingQuantity();
                if (totalAvailable >= order.getOriginalQuantity()) {
                    return totalAvailable;
                }
            }
        }

        return totalAvailable;
    }

    // ────────────────────────────── Matching ──────────────────────────────

    /**
     * Walks the opposite side's price levels and matches as long as prices cross.
     *
     * @param incoming     aggressor order
     * @param oppositeSide resting side (asks for a buy, bids for a sell)
     * @param trades       accumulator
     * @param incomingIsBuy true when the aggressor is a BUY
     */
    private void matchAgainst(Order incoming,
                              TreeMap<Long, PriceLevel> oppositeSide,
                              List<Trade> trades,
                              boolean incomingIsBuy) {

        Iterator<Map.Entry<Long, PriceLevel>> it = oppositeSide.entrySet().iterator();

        while (!incoming.isFilled() && it.hasNext()) {
            Map.Entry<Long, PriceLevel> entry = it.next();
            long restingPrice = entry.getKey();

            // Price-cross check
            if (incomingIsBuy && incoming.getPrice() < restingPrice) break;
            if (!incomingIsBuy && incoming.getPrice() > restingPrice) break;

            PriceLevel level = entry.getValue();

            // Consume orders at this level (FIFO)
            while (!incoming.isFilled() && !level.isEmpty()) {
                Order resting = level.peek();
                long matchQty = Math.min(incoming.getRemainingQuantity(),
                                         resting.getRemainingQuantity());

                incoming.reduceQuantity(matchQty);
                resting.reduceQuantity(matchQty);

                long buyId  = incomingIsBuy ? incoming.getId() : resting.getId();
                long sellId = incomingIsBuy ? resting.getId()  : incoming.getId();

                trades.add(new Trade(
                        tradeIdSupplier.getAsLong(),
                        buyId,
                        sellId,
                        incoming.getSymbol(),
                        restingPrice,          // execute at resting price
                        matchQty,
                        System.nanoTime()));

                if (resting.isFilled()) {
                    level.poll();
                    orderIndex.remove(resting.getId());
                }
            }

            // Remove empty price level
            if (level.isEmpty()) {
                it.remove();
            }
        }
    }

    /**
     * Inserts an unfilled (or partially filled) order into the correct side.
     */
    private void insertIntoBook(Order order) {
        TreeMap<Long, PriceLevel> side = (order.getSide() == Side.BUY) ? bids : asks;
        side.computeIfAbsent(order.getPrice(), PriceLevel::new).addOrder(order);
        orderIndex.put(order.getId(), order);
    }

    // ────────────────────────────── Cancellation ──────────────────────────────

    /**
     * Cancels an order by ID. Removes it from the price level queue and cleans
     * up empty levels to prevent memory leaks.
     *
     * @param orderId order to cancel
     * @param side    expected side
     * @return true if found and removed
     */
    public boolean cancelOrder(long orderId, Side side) {
        Order order = orderIndex.get(orderId);
        if (order == null || order.getSide() != side) {
            return false;
        }

        TreeMap<Long, PriceLevel> book = (side == Side.BUY) ? bids : asks;
        PriceLevel level = book.get(order.getPrice());
        if (level == null) {
            return false;
        }

        boolean removed = level.removeOrder(orderId);
        if (removed) {
            orderIndex.remove(orderId);
            if (level.isEmpty()) {
                book.remove(order.getPrice());
            }
        }
        return removed;
    }

    // ────────────────────────────── Inspection ──────────────────────────────

    public Long getBestBidPrice() {
        return bids.isEmpty() ? null : bids.firstKey();
    }

    public Long getBestAskPrice() {
        return asks.isEmpty() ? null : asks.firstKey();
    }

    public int getBidLevelCount() {
        return bids.size();
    }

    public int getAskLevelCount() {
        return asks.size();
    }

    public boolean containsOrder(long orderId) {
        return orderIndex.containsKey(orderId);
    }

    public int getTotalOrderCount() {
        return orderIndex.size();
    }

    public TreeMap<Long, PriceLevel> getBids() {
        return bids;
    }

    public TreeMap<Long, PriceLevel> getAsks() {
        return asks;
    }
}
