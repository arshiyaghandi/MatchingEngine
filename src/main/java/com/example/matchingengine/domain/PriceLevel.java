package com.example.matchingengine.domain;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Represents a specific price level in the OrderBook.
 * Maintains a FIFO queue of orders at this price.
 */
public class PriceLevel {
    private final long price;
    private final Queue<Order> orders;

    public PriceLevel(long price) {
        this.price = price;
        this.orders = new LinkedList<>();
    }

    public long getPrice() {
        return price;
    }

    public void addOrder(Order order) {
        if (order.getPrice() != this.price) {
            throw new IllegalArgumentException("Order price does not match PriceLevel price");
        }
        orders.add(order);
    }

    public Order peek() {
        return orders.peek();
    }

    public Order poll() {
        return orders.poll();
    }

    public boolean isEmpty() {
        return orders.isEmpty();
    }
    
    public boolean removeOrder(long orderId) {
        return orders.removeIf(order -> order.getId() == orderId);
    }

    public Queue<Order> getOrders() {
        return orders;
    }
}
