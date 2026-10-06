package com.example.matchingengine;

import com.example.matchingengine.domain.Order;
import com.example.matchingengine.domain.OrderType;
import com.example.matchingengine.domain.Side;
import com.example.matchingengine.domain.Trade;
import com.example.matchingengine.engine.MatchingEngine;
import com.example.matchingengine.exception.InvalidOrderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the core {@link MatchingEngine} (no Spring context).
 *
 * <p>Focuses on engine-level concerns: duplicate ID protection,
 * multi-symbol isolation, and correct delegation to {@code OrderBook}.</p>
 */
class MatchingEngineTest {

    private MatchingEngine engine;
    private long idGenerator;

    @BeforeEach
    void setUp() {
        engine = new MatchingEngine();
        idGenerator = 1;
    }

    private Order buy(String symbol, long price, long qty) {
        return new Order(idGenerator++, symbol, Side.BUY, OrderType.LIMIT, price, qty, System.nanoTime());
    }

    private Order sell(String symbol, long price, long qty) {
        return new Order(idGenerator++, symbol, Side.SELL, OrderType.LIMIT, price, qty, System.nanoTime());
    }

    @Test
    @DisplayName("Submit BUY — no match, order rests in book")
    void submitBuyNoMatch() {
        Order buyOrder = buy("AAPL", 15000, 100);
        List<Trade> trades = engine.submitOrder(buyOrder);

        assertTrue(trades.isEmpty());
        assertEquals(100, buyOrder.getRemainingQuantity());
        assertTrue(engine.hasOrder(buyOrder.getId()));
    }

    @Test
    @DisplayName("Submit SELL — no match, order rests in book")
    void submitSellNoMatch() {
        Order sellOrder = sell("AAPL", 15000, 100);
        List<Trade> trades = engine.submitOrder(sellOrder);

        assertTrue(trades.isEmpty());
        assertEquals(100, sellOrder.getRemainingQuantity());
    }

    @Test
    @DisplayName("Full fill through engine")
    void fullFill() {
        Order buyOrder = buy("AAPL", 10000, 100);
        engine.submitOrder(buyOrder);

        Order sellOrder = sell("AAPL", 10000, 100);
        List<Trade> trades = engine.submitOrder(sellOrder);

        assertEquals(1, trades.size());
        assertEquals(100, trades.get(0).getQuantity());
        assertEquals(0, buyOrder.getRemainingQuantity());
        assertEquals(0, sellOrder.getRemainingQuantity());
    }

    @Test
    @DisplayName("Partial fill through engine")
    void partialFill() {
        Order buyOrder = buy("AAPL", 10000, 100);
        engine.submitOrder(buyOrder);

        Order sellOrder = sell("AAPL", 10000, 40);
        List<Trade> trades = engine.submitOrder(sellOrder);

        assertEquals(1, trades.size());
        assertEquals(40, trades.get(0).getQuantity());
        assertEquals(60, buyOrder.getRemainingQuantity());
    }

    @Test
    @DisplayName("Duplicate order ID throws InvalidOrderException")
    void duplicateOrderIdThrows() {
        Order order1 = new Order(42, "AAPL", Side.BUY, OrderType.LIMIT, 10000, 100, System.nanoTime());
        engine.submitOrder(order1);

        Order order2 = new Order(42, "AAPL", Side.SELL, OrderType.LIMIT, 10000, 50, System.nanoTime());
        assertThrows(InvalidOrderException.class, () -> engine.submitOrder(order2));
    }

    @Test
    @DisplayName("Cancel removes order from book")
    void cancelOrder() {
        Order buyOrder = buy("AAPL", 10000, 100);
        engine.submitOrder(buyOrder);

        boolean cancelled = engine.cancelOrder("AAPL", buyOrder.getId(), Side.BUY);
        assertTrue(cancelled);
        assertFalse(engine.hasOrder(buyOrder.getId()));

        // Sell should not match the cancelled order
        Order sellOrder = sell("AAPL", 10000, 100);
        List<Trade> trades = engine.submitOrder(sellOrder);
        assertTrue(trades.isEmpty());
    }

    @Test
    @DisplayName("Cancel non-existent order returns false")
    void cancelNonExistent() {
        assertFalse(engine.cancelOrder("AAPL", 999, Side.BUY));
    }

    @Test
    @DisplayName("Different symbols are isolated")
    void multiSymbolIsolation() {
        Order aaplBuy = buy("AAPL", 10000, 100);
        Order googlSell = sell("GOOGL", 10000, 100);

        engine.submitOrder(aaplBuy);
        List<Trade> trades = engine.submitOrder(googlSell);

        // Should NOT match — different symbols
        assertTrue(trades.isEmpty());
        assertEquals(100, aaplBuy.getRemainingQuantity());
        assertEquals(100, googlSell.getRemainingQuantity());
    }

    @Test
    @DisplayName("No match when buy price < sell price")
    void noMatchWhenBuyBelowSell() {
        engine.submitOrder(sell("AAPL", 10100, 100));
        List<Trade> trades = engine.submitOrder(buy("AAPL", 10000, 50));

        assertTrue(trades.isEmpty());
    }

    @Test
    @DisplayName("Multiple orders at different prices")
    void multipleOrdersDifferentPrices() {
        engine.submitOrder(sell("AAPL", 10100, 50));
        engine.submitOrder(sell("AAPL", 10200, 50));

        Order aggressiveBuy = buy("AAPL", 10300, 75);
        List<Trade> trades = engine.submitOrder(aggressiveBuy);

        assertEquals(2, trades.size());
        assertEquals(10100, trades.get(0).getPrice());
        assertEquals(50, trades.get(0).getQuantity());
        assertEquals(10200, trades.get(1).getPrice());
        assertEquals(25, trades.get(1).getQuantity());
    }
}
