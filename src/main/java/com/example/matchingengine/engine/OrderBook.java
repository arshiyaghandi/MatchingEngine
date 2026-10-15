package com.example.matchingengine.engine;

import com.example.matchingengine.domain.Order;
import com.example.matchingengine.domain.OrderType;
import com.example.matchingengine.domain.PriceLevel;
import com.example.matchingengine.domain.Side;
import com.example.matchingengine.domain.StopOrder;
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
 * <h3>Stop Orders</h3>
 * <p>STOP_LIMIT and STOP_MARKET orders rest until activated by an execution price:
 * <ul>
 *   <li>Stop BUY triggers when execution price &gt;= stopPrice</li>
 *   <li>Stop SELL triggers when execution price &lt;= stopPrice</li>
 * </ul>
 * Upon triggering, stop orders are converted to regular LIMIT/MARKET orders and matched.</p>
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

    /** Buy stop orders: lowest stopPrice first (natural order). */
    private final TreeMap<Long, List<StopOrder>> buyStopOrders;

    /** Sell stop orders: highest stopPrice first (reverse natural order). */
    private final TreeMap<Long, List<StopOrder>> sellStopOrders;

    /** Fast O(1) lookup for pending stop orders by order ID. */
    private final Map<Long, StopOrder> stopOrderIndex;

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
        this.buyStopOrders = new TreeMap<>();
        this.sellStopOrders = new TreeMap<>(Collections.reverseOrder());
        this.stopOrderIndex = new HashMap<>();
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
     * <p>STOP_LIMIT and STOP_MARKET orders do not match immediately; they are placed
     * into the stop order book until triggered by a trade execution price.</p>
     *
     * @param order the incoming order (aggressor)
     * @return trades generated (may be empty)
     */
    public List<Trade> addOrder(Order order) {
        if (order.getType() == OrderType.STOP_LIMIT || order.getType() == OrderType.STOP_MARKET) {
            addStopOrder(order);
            return Collections.emptyList();
        }

        List<Trade> trades = new ArrayList<>();
        matchAndRest(order, trades);

        if (!trades.isEmpty()) {
            triggerStopOrders(trades);
        }

        return trades;
    }

    /**
     * Places a stop order into the buy or sell stop book.
     */
    private void addStopOrder(Order order) {
        StopOrder stopOrder = new StopOrder(order);
        TreeMap<Long, List<StopOrder>> stopBook = (order.getSide() == Side.BUY) ? buyStopOrders : sellStopOrders;
        stopBook.computeIfAbsent(stopOrder.getStopPrice(), k -> new ArrayList<>()).add(stopOrder);
        stopOrderIndex.put(order.getId(), stopOrder);
    }

    /**
     * Matches an order against the resting book and inserts any unfilled remainder if allowed.
     */
    private void matchAndRest(Order order, List<Trade> trades) {
        // FOK (Fill-Or-Kill): check if full quantity can be matched immediately
        if (order.getType() == OrderType.FOK) {
            long available = getAvailableMatchingQuantity(order);
            if (available < order.getOriginalQuantity()) {
                // Cannot fill completely — kill the order immediately without any partial match
                return;
            }
        }

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
    }

    /**
     * Checks if any pending stop orders are triggered by trades that executed.
     * Triggered orders are activated into regular LIMIT or MARKET orders and matched.
     * Any new trades generated in turn check for further cascading stop triggers.
     */
    private void triggerStopOrders(List<Trade> trades) {
        int processedTradeIndex = 0;
        while (processedTradeIndex < trades.size()) {
            Trade trade = trades.get(processedTradeIndex++);
            long executionPrice = trade.getPrice();
            List<StopOrder> triggered = collectTriggeredStopOrders(executionPrice);
            for (StopOrder stopOrder : triggered) {
                Order activatedOrder = stopOrder.toActivatedOrder();
                matchAndRest(activatedOrder, trades);
            }
        }
    }

    /**
     * Collects and removes all stop orders triggered at the given execution price.
     *
     * <ul>
     *   <li>Buy stops trigger when executionPrice >= stopPrice (stopPrice <= executionPrice)</li>
     *   <li>Sell stops trigger when executionPrice <= stopPrice (stopPrice >= executionPrice)</li>
     * </ul>
     */
    private List<StopOrder> collectTriggeredStopOrders(long executionPrice) {
        List<StopOrder> triggered = new ArrayList<>();

        // Stop BUY orders trigger when market price >= stopPrice (i.e. stopPrice <= executionPrice)
        List<Long> buyKeysToRemove = new ArrayList<>();
        for (Map.Entry<Long, List<StopOrder>> entry : buyStopOrders.headMap(executionPrice, true).entrySet()) {
            buyKeysToRemove.add(entry.getKey());
            for (StopOrder so : entry.getValue()) {
                stopOrderIndex.remove(so.getOrderId());
                triggered.add(so);
            }
        }
        for (Long key : buyKeysToRemove) {
            buyStopOrders.remove(key);
        }

        // Stop SELL orders trigger when market price <= stopPrice (i.e. stopPrice >= executionPrice)
        List<Long> sellKeysToRemove = new ArrayList<>();
        for (Map.Entry<Long, List<StopOrder>> entry : sellStopOrders.headMap(executionPrice, true).entrySet()) {
            sellKeysToRemove.add(entry.getKey());
            for (StopOrder so : entry.getValue()) {
                stopOrderIndex.remove(so.getOrderId());
                triggered.add(so);
            }
        }
        for (Long key : sellKeysToRemove) {
            sellStopOrders.remove(key);
        }

        return triggered;
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

    /**
     * Cancels a pending stop order by ID.
     *
     * @param orderId ID of the stop order to cancel
     * @return true if found and removed, false otherwise
     */
    public boolean cancelStopOrder(long orderId) {
        StopOrder stopOrder = stopOrderIndex.remove(orderId);
        if (stopOrder == null) {
            return false;
        }

        TreeMap<Long, List<StopOrder>> stopBook = (stopOrder.getSide() == Side.BUY) ? buyStopOrders : sellStopOrders;
        List<StopOrder> list = stopBook.get(stopOrder.getStopPrice());
        if (list != null) {
            list.remove(stopOrder);
            if (list.isEmpty()) {
                stopBook.remove(stopOrder.getStopPrice());
            }
        }
        return true;
    }

    /**
     * Cancels a pending stop order by ID and verifies the expected side.
     *
     * @param orderId ID of the stop order to cancel
     * @param side    expected side
     * @return true if found, matches side, and removed; false otherwise
     */
    public boolean cancelStopOrder(long orderId, Side side) {
        StopOrder stopOrder = stopOrderIndex.get(orderId);
        if (stopOrder == null || (side != null && stopOrder.getSide() != side)) {
            return false;
        }
        return cancelStopOrder(orderId);
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

    public int getBuyStopCount() {
        return buyStopOrders.values().stream().mapToInt(List::size).sum();
    }

    public int getSellStopCount() {
        return sellStopOrders.values().stream().mapToInt(List::size).sum();
    }

    public int getStopOrderCount() {
        return stopOrderIndex.size();
    }

    public int getTotalStopOrderCount() {
        return stopOrderIndex.size();
    }

    public int getBuyStopLevelCount() {
        return buyStopOrders.size();
    }

    public int getSellStopLevelCount() {
        return sellStopOrders.size();
    }

    public boolean containsStopOrder(long orderId) {
        return stopOrderIndex.containsKey(orderId);
    }

    public StopOrder getStopOrder(long orderId) {
        return stopOrderIndex.get(orderId);
    }

    public TreeMap<Long, List<StopOrder>> getBuyStopOrders() {
        return buyStopOrders;
    }

    public TreeMap<Long, List<StopOrder>> getSellStopOrders() {
        return sellStopOrders;
    }
}

