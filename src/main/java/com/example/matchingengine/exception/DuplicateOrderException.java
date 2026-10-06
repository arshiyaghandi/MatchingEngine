package com.example.matchingengine.exception;

/**
 * Thrown when an order submitted to the engine fails domain-level validation
 * (e.g. duplicate ID, invalid symbol, etc.).
 */
public class DuplicateOrderException extends InvalidOrderException {
    public DuplicateOrderException(long orderId) {
        super("Duplicate order ID: " + orderId);
    }
}
