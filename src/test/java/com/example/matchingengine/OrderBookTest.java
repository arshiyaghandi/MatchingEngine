package com.example.matchingengine;

import com.example.matchingengine.domain.Order;
import com.example.matchingengine.domain.OrderType;
import com.example.matchingengine.domain.Side;
import com.example.matchingengine.domain.Trade;
import com.example.matchingengine.engine.OrderBook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests for {@link OrderBook}. No Spring context needed.
 */
class OrderBookTest {

    private OrderBook book;
    private long idCounter;

    @BeforeEach
    void setUp() {
        book = new OrderBook("AAPL");
        idCounter = 1;
    }

    private Order buy(long price, long qty) {
        return new Order(idCounter++, "AAPL", Side.BUY, OrderType.LIMIT, price, qty, System.nanoTime());
    }

    private Order sell(long price, long qty) {
        return new Order(idCounter++, "AAPL", Side.SELL, OrderType.LIMIT, price, qty, System.nanoTime());
    }

    // ────────────────────── Basic Insertion ──────────────────────

    @Nested
    @DisplayName("Order Insertion")
    class Insertion {

        @Test
        @DisplayName("BUY order with no opposing side → rests in book, no trades")
        void buyOrderRestsWhenNoAsks() {
            List<Trade> trades = book.addOrder(buy(10000, 100));
            assertTrue(trades.isEmpty());
            assertEquals(10000L, book.getBestBidPrice());
            assertEquals(1, book.getTotalOrderCount());
        }

        @Test
        @DisplayName("SELL order with no opposing side → rests in book, no trades")
        void sellOrderRestsWhenNoBids() {
            List<Trade> trades = book.addOrder(sell(10000, 100));
            assertTrue(trades.isEmpty());
            assertEquals(10000L, book.getBestAskPrice());
            assertEquals(1, book.getTotalOrderCount());
        }
    }

    // ────────────────────── Sorting / Priority ──────────────────────

    @Nested
    @DisplayName("Price-Time Priority")
    class Priority {

        @Test
        @DisplayName("Bids sorted descending — highest price is best bid")
        void bidsSortedDescending() {
            book.addOrder(buy(10000, 10));
            book.addOrder(buy(10100, 10));
            book.addOrder(buy(9900, 10));

            assertEquals(10100L, book.getBestBidPrice());
            assertEquals(3, book.getBidLevelCount());
        }

        @Test
        @DisplayName("Asks sorted ascending — lowest price is best ask")
        void asksSortedAscending() {
            book.addOrder(sell(10200, 10));
            book.addOrder(sell(10000, 10));
            book.addOrder(sell(10100, 10));

            assertEquals(10000L, book.getBestAskPrice());
            assertEquals(3, book.getAskLevelCount());
        }

        @Test
        @DisplayName("Same-price orders matched in FIFO order")
        void samePriceFIFO() {
            Order buy1 = buy(10000, 10);
            Order buy2 = buy(10000, 20);
            book.addOrder(buy1);
            book.addOrder(buy2);

            // Sell 15 should fill buy1 (10) entirely and buy2 partially (5)
            Order sellOrder = sell(10000, 15);
            List<Trade> trades = book.addOrder(sellOrder);

            assertEquals(2, trades.size());

            // First trade consumes buy1 entirely
            assertEquals(buy1.getId(), trades.get(0).getBuyOrderId());
            assertEquals(10, trades.get(0).getQuantity());

            // Second trade partially fills buy2
            assertEquals(buy2.getId(), trades.get(1).getBuyOrderId());
            assertEquals(5, trades.get(1).getQuantity());

            assertEquals(0, buy1.getRemainingQuantity());
            assertEquals(15, buy2.getRemainingQuantity());
            assertEquals(0, sellOrder.getRemainingQuantity());
        }
    }

    // ────────────────────── Matching ──────────────────────

    @Nested
    @DisplayName("Matching")
    class Matching {

        @Test
        @DisplayName("No match when buy price < sell price")
        void noMatchWhenBuyBelowAsk() {
            book.addOrder(sell(10100, 100));
            List<Trade> trades = book.addOrder(buy(10000, 50));

            assertTrue(trades.isEmpty());
            assertEquals(2, book.getTotalOrderCount());
        }

        @Test
        @DisplayName("Full fill — equal quantities at same price")
        void fullFill() {
            Order buyOrder = buy(10000, 100);
            book.addOrder(buyOrder);

            Order sellOrder = sell(10000, 100);
            List<Trade> trades = book.addOrder(sellOrder);

            assertEquals(1, trades.size());
            assertEquals(100, trades.get(0).getQuantity());
            assertEquals(10000, trades.get(0).getPrice());
            assertEquals(0, buyOrder.getRemainingQuantity());
            assertEquals(0, sellOrder.getRemainingQuantity());
            assertEquals(0, book.getTotalOrderCount());
        }

        @Test
        @DisplayName("Partial fill — BUY 100, SELL 40 → BUY remains 60")
        void partialFillBuyRemains() {
            Order buyOrder = buy(10000, 100);
            book.addOrder(buyOrder);

            Order sellOrder = sell(10000, 40);
            List<Trade> trades = book.addOrder(sellOrder);

            assertEquals(1, trades.size());
            assertEquals(40, trades.get(0).getQuantity());
            assertEquals(60, buyOrder.getRemainingQuantity());
            assertEquals(0, sellOrder.getRemainingQuantity());
            assertTrue(book.containsOrder(buyOrder.getId()));
            assertFalse(book.containsOrder(sellOrder.getId()));
        }

        @Test
        @DisplayName("Partial fill — SELL 100, BUY 40 → SELL remains 60")
        void partialFillSellRemains() {
            Order sellOrder = sell(10000, 100);
            book.addOrder(sellOrder);

            Order buyOrder = buy(10000, 40);
            List<Trade> trades = book.addOrder(buyOrder);

            assertEquals(1, trades.size());
            assertEquals(40, trades.get(0).getQuantity());
            assertEquals(60, sellOrder.getRemainingQuantity());
            assertEquals(0, buyOrder.getRemainingQuantity());
        }

        @Test
        @DisplayName("Multiple price levels consumed — aggressive buy sweeps asks")
        void multipleOrdersDifferentPrices() {
            Order sell1 = sell(10100, 50);
            Order sell2 = sell(10200, 50);
            book.addOrder(sell1);
            book.addOrder(sell2);

            Order aggressiveBuy = buy(10300, 75);
            List<Trade> trades = book.addOrder(aggressiveBuy);

            assertEquals(2, trades.size());

            // First trade at 10100 (lower ask)
            assertEquals(10100, trades.get(0).getPrice());
            assertEquals(50, trades.get(0).getQuantity());

            // Second trade at 10200
            assertEquals(10200, trades.get(1).getPrice());
            assertEquals(25, trades.get(1).getQuantity());

            assertEquals(0, aggressiveBuy.getRemainingQuantity());
            assertEquals(25, sell2.getRemainingQuantity());
        }

        @Test
        @DisplayName("Multiple orders at same price — FIFO consumed")
        void multipleOrdersSamePrice() {
            Order sell1 = sell(10000, 30);
            Order sell2 = sell(10000, 40);
            book.addOrder(sell1);
            book.addOrder(sell2);

            Order buyOrder = buy(10000, 50);
            List<Trade> trades = book.addOrder(buyOrder);

            assertEquals(2, trades.size());
            assertEquals(30, trades.get(0).getQuantity()); // sell1 fully filled
            assertEquals(20, trades.get(1).getQuantity()); // sell2 partially filled
            assertEquals(0, buyOrder.getRemainingQuantity());
            assertEquals(20, sell2.getRemainingQuantity());
        }

        @Test
        @DisplayName("Execution price is the resting order's price")
        void executionPriceIsRestingPrice() {
            book.addOrder(sell(9900, 100)); // resting ask at 9900

            // Aggressive buy at 10000 — should execute at 9900 (resting price)
            Order buyOrder = buy(10000, 50);
            List<Trade> trades = book.addOrder(buyOrder);

            assertEquals(1, trades.size());
            assertEquals(9900, trades.get(0).getPrice());
        }
    }

    // ────────────────────── Cancellation ──────────────────────

    @Nested
    @DisplayName("Cancellation")
    class Cancellation {

        @Test
        @DisplayName("Cancel existing order removes it from book")
        void cancelExisting() {
            Order buyOrder = buy(10000, 100);
            book.addOrder(buyOrder);

            boolean cancelled = book.cancelOrder(buyOrder.getId(), Side.BUY);
            assertTrue(cancelled);
            assertFalse(book.containsOrder(buyOrder.getId()));
            assertEquals(0, book.getTotalOrderCount());
        }

        @Test
        @DisplayName("Cancel non-existent order returns false")
        void cancelNonExistent() {
            assertFalse(book.cancelOrder(999, Side.BUY));
        }

        @Test
        @DisplayName("Cancel with wrong side returns false")
        void cancelWrongSide() {
            Order buyOrder = buy(10000, 100);
            book.addOrder(buyOrder);

            assertFalse(book.cancelOrder(buyOrder.getId(), Side.SELL));
            assertTrue(book.containsOrder(buyOrder.getId())); // still in book
        }

        @Test
        @DisplayName("Cancel cleans up empty price level")
        void cancelCleansEmptyLevel() {
            Order buyOrder = buy(10000, 100);
            book.addOrder(buyOrder);
            assertEquals(1, book.getBidLevelCount());

            book.cancelOrder(buyOrder.getId(), Side.BUY);
            assertEquals(0, book.getBidLevelCount());
            assertNull(book.getBestBidPrice());
        }

        @Test
        @DisplayName("Cancelled order cannot match")
        void cancelledOrderCannotMatch() {
            Order buyOrder = buy(10000, 100);
            book.addOrder(buyOrder);
            book.cancelOrder(buyOrder.getId(), Side.BUY);

            Order sellOrder = sell(10000, 100);
            List<Trade> trades = book.addOrder(sellOrder);

            assertTrue(trades.isEmpty());
        }
    }

    // ────────────────────── Validation ──────────────────────

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("Price must be positive")
        void priceMustBePositive() {
            assertThrows(IllegalArgumentException.class, () -> buy(0, 100));
            assertThrows(IllegalArgumentException.class, () -> buy(-100, 100));
        }

        @Test
        @DisplayName("Quantity must be positive")
        void quantityMustBePositive() {
            assertThrows(IllegalArgumentException.class, () -> buy(10000, 0));
            assertThrows(IllegalArgumentException.class, () -> buy(10000, -10));
        }

        @Test
        @DisplayName("Symbol cannot be empty or null")
        void symbolCannotBeEmpty() {
            assertThrows(IllegalArgumentException.class, () ->
                    new Order(1, "", Side.BUY, OrderType.LIMIT, 10000, 100, System.nanoTime()));
            assertThrows(IllegalArgumentException.class, () ->
                    new Order(1, null, Side.BUY, OrderType.LIMIT, 10000, 100, System.nanoTime()));
        }
    }
}
