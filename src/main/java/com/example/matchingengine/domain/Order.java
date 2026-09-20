package com.example.matchingengine.domain;

import java.util.Objects;

/**
 * Represents an Order in the system.
 * Prices and quantities are represented as long integers to avoid floating point precision issues.
 * E.g., a price of 35,120.50 might be represented as 3512050 (scale factor 100).
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
        if (price <= 0) {
            throw new IllegalArgumentException("Price must be strictly positive");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be strictly positive");
        }
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be empty");
        }
        
        this.id = id;
        this.symbol = symbol;
        this.side = side;
        this.type = type;
        this.price = price;
        this.originalQuantity = quantity;
        this.remainingQuantity = quantity;
        this.timestamp = timestamp;
    }

    public long getId() { return id; }
    public String getSymbol() { return symbol; }
    public Side getSide() { return side; }
    public OrderType getType() { return type; }
    public long getPrice() { return price; }
    public long getOriginalQuantity() { return originalQuantity; }
    public long getRemainingQuantity() { return remainingQuantity; }
    public long getTimestamp() { return timestamp; }

    public void reduceQuantity(long amount) {
        if (amount <= 0 || amount > remainingQuantity) {
            throw new IllegalArgumentException("Invalid amount to reduce");
        }
        this.remainingQuantity -= amount;
    }

    public boolean isFilled() {
        return remainingQuantity == 0;
    }

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
}
