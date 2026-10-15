package com.example.matchingengine.domain;

/**
 * Supported order types.
 *
 * <ul>
 *   <li>{@link #LIMIT} — executes at the specified price or better; unfilled
 *       remainder rests in the order book.</li>
 *   <li>{@link #MARKET} — executes immediately at the best available price;
 *       unfilled remainder is <em>not</em> placed in the book.</li>
 *   <li>{@link #IOC} — Immediate-Or-Cancel: executes as much as possible immediately,
 *       and cancels any unfilled portion without resting in the book.</li>
 *   <li>{@link #FOK} — Fill-Or-Kill: must be filled completely immediately,
 *       otherwise the entire order is killed/cancelled (no partial fills).</li>
 *   <li>{@link #STOP_LIMIT} — activates into a regular {@link #LIMIT} order at the
 *       specified limit price when the market price reaches or crosses the stop price.</li>
 *   <li>{@link #STOP_MARKET} — activates into a regular {@link #MARKET} order when
 *       the market price reaches or crosses the stop price.</li>
 * </ul>
 */
public enum OrderType {
    LIMIT,
    MARKET,
    IOC,
    FOK,
    STOP_LIMIT,
    STOP_MARKET
}

