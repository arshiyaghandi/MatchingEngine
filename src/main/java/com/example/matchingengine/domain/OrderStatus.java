package com.example.matchingengine.domain;

/**
 * Lifecycle states of an {@link Order} within the matching engine.
 *
 * <pre>
 *   ACTIVE ──┬──▶ PARTIALLY_FILLED ──┬──▶ FILLED
 *            │                        │
 *            └──▶ CANCELLED           └──▶ CANCELLED
 * </pre>
 */
public enum OrderStatus {

    /** Resting in the order book, not yet matched. */
    ACTIVE,

    /** Partially executed — some quantity remains. */
    PARTIALLY_FILLED,

    /** Fully executed — remaining quantity is zero. */
    FILLED,

    /** Cancelled by the user or system. */
    CANCELLED;

    /**
     * Derives the appropriate status from remaining vs original quantity.
     *
     * @param originalQty   the original order quantity
     * @param remainingQty  the current remaining quantity
     * @return the computed status
     */
    public static OrderStatus fromQuantities(long originalQty, long remainingQty) {
        if (remainingQty == 0) return FILLED;
        if (remainingQty < originalQty) return PARTIALLY_FILLED;
        return ACTIVE;
    }
}
