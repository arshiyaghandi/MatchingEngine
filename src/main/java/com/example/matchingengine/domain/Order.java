package com.example.matchingengine.domain;

import java.util.Objects;

/**
 * Represents an Order in the system.
 *
 * <p>Prices and quantities are represented as {@code long} integers to avoid
 * floating-point precision issues. A scale factor of 100 means 2 decimal
 * places: e.g. a human price of 35,120.50 is stored as {@code 3_512_050}.</p>
 *
 * <h3>MARKET Order Handling</h3>
 * <p>MARKET orders use sentinel prices that guarantee a price-cross against
 * any resting order:
 * <ul>
 *   <li>{@code BUY MARKET} → price = {@link Long#MAX_VALUE}</li>
 *   <li>{@code SELL MARKET} → price = 1</li>
 * </ul>
 * After matching, unfilled MARKET orders are <em>not</em> placed in the book.</p>
 */
public class Order {

    private final long id;
    private final String symbol;
    private final Side side;
    private final OrderType type;
    private final long price;
    private final long originalQuantity;
    private long remainingQuantity;
    private final long timestamp;

    public Order(long id, String symbol, Side side, OrderType type, long price, long quantity, long timestamp) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be strictly positive");
        }
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol cannot be empty");
        }

        this.id = id;
        this.symbol = symbol;
        this.side = side;
        this.type = type;
        this.originalQuantity = quantity;
        this.remainingQuantity = quantity;
        this.timestamp = timestamp;

        if (type == OrderType.MARKET) {
            // Sentinel prices guarantee a cross against any resting order
            this.price = (side == Side.BUY) ? Long.MAX_VALUE : 1L;
        } else {
            if (price <= 0) {
                throw new IllegalArgumentException("Price must be strictly positive");
            }
            this.price = price;
        }
    }

    // ────────────────────────── Accessors ──────────────────────────

    public long getId() { return id; }
    public String getSymbol() { return symbol; }
    public Side getSide() { return side; }
    public OrderType getType() { return type; }
    public long getPrice() { return price; }
    public long getOriginalQuantity() { return originalQuantity; }
    public long getRemainingQuantity() { return remainingQuantity; }
    public long getTimestamp() { return timestamp; }

    // ────────────────────────── Mutators ──────────────────────────

    public void reduceQuantity(long amount) {
        if (amount <= 0 || amount > remainingQuantity) {
            throw new IllegalArgumentException("Invalid amount to reduce");
        }
        this.remainingQuantity -= amount;
    }

    public boolean isFilled() {
        return remainingQuantity == 0;
    }

    // ────────────────────────── Object ──────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Order order = (Order) o;
        return id == order.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Order{id=%d, symbol='%s', side=%s, type=%s, price=%d, qty=%d/%d}"
                .formatted(id, symbol, side, type, price, remainingQuantity, originalQuantity);
    }
}
