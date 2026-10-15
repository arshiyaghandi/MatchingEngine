package com.example.matchingengine.domain;

import java.util.Objects;

/**
 * Represents a Stop Order resting until a trigger price condition is met.
 *
 * <p>Wraps the original {@link Order} details along with:
 * <ul>
 *   <li>{@code stopPrice} — activation trigger price (fixed-point)</li>
 *   <li>{@code limitPrice} — limit execution price once triggered (for {@link OrderType#STOP_LIMIT})</li>
 *   <li>{@code triggered} — boolean flag indicating if the order has been activated</li>
 * </ul>
 *
 * <h3>Trigger Conditions</h3>
 * <ul>
 *   <li>Stop BUY orders trigger when {@code marketPrice >= stopPrice}</li>
 *   <li>Stop SELL orders trigger when {@code marketPrice <= stopPrice}</li>
 * </ul>
 */
public class StopOrder {

    private final Order order;
    private final long stopPrice;
    private final long limitPrice;
    private boolean triggered;

    /**
     * Constructs a StopOrder with explicit stop and limit prices.
     *
     * @param order      the underlying order
     * @param stopPrice  the activation trigger price (fixed-point)
     * @param limitPrice the limit price if this is a STOP_LIMIT order (0 for STOP_MARKET)
     */
    public StopOrder(Order order, long stopPrice, long limitPrice) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }
        if (stopPrice <= 0) {
            throw new IllegalArgumentException("Stop price must be strictly positive");
        }
        this.order = order;
        this.stopPrice = stopPrice;
        this.limitPrice = limitPrice;
        this.triggered = false;
    }

    /**
     * Constructs a StopOrder from an Order instance.
     *
     * @param order the order containing stop price and limit price
     */
    public StopOrder(Order order) {
        this(order, order.getStopPrice(), order.getType() == OrderType.STOP_LIMIT ? order.getPrice() : 0L);
    }

    // ────────────────────────── Accessors ──────────────────────────

    public Order getOrder() {
        return order;
    }

    public long getOrderId() {
        return order.getId();
    }

    public String getSymbol() {
        return order.getSymbol();
    }

    public Side getSide() {
        return order.getSide();
    }

    public OrderType getType() {
        return order.getType();
    }

    public long getStopPrice() {
        return stopPrice;
    }

    public long getLimitPrice() {
        return limitPrice;
    }

    public boolean isTriggered() {
        return triggered;
    }

    // ────────────────────────── Mutators ──────────────────────────

    public void setTriggered(boolean triggered) {
        this.triggered = triggered;
    }

    public void trigger() {
        this.triggered = true;
    }

    /**
     * Converts this stop order into an activated regular {@link OrderType#LIMIT}
     * or {@link OrderType#MARKET} order ready for matching in the order book.
     *
     * @return activated regular Order
     */
    public Order toActivatedOrder() {
        this.triggered = true;
        OrderType activatedType = (order.getType() == OrderType.STOP_LIMIT) ? OrderType.LIMIT : OrderType.MARKET;
        long activatedPrice = (activatedType == OrderType.LIMIT) ? limitPrice : 0L;
        return new Order(
                order.getId(),
                order.getSymbol(),
                order.getSide(),
                activatedType,
                activatedPrice,
                order.getRemainingQuantity(),
                System.nanoTime()
        );
    }

    // ────────────────────────── Object ──────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StopOrder stopOrder = (StopOrder) o;
        return order.getId() == stopOrder.order.getId();
    }

    @Override
    public int hashCode() {
        return Objects.hash(order.getId());
    }

    @Override
    public String toString() {
        return "StopOrder{orderId=%d, symbol='%s', side=%s, type=%s, stopPrice=%d, limitPrice=%d, triggered=%s}"
                .formatted(order.getId(), order.getSymbol(), order.getSide(), order.getType(), stopPrice, limitPrice, triggered);
    }
}
