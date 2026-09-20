package com.example.matchingengine;

import com.example.matchingengine.domain.Order;
import com.example.matchingengine.domain.OrderType;
import com.example.matchingengine.domain.Side;
import com.example.matchingengine.domain.Trade;
import com.example.matchingengine.engine.MatchingEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MatchingEngineTest {

    private MatchingEngine engine;
    private long idGenerator;

    @BeforeEach
    void setUp() {
        engine = new MatchingEngine();
        idGenerator = 1;
    }

    private Order createBuyOrder(String symbol, long price, long quantity) {
        return new Order(idGenerator++, symbol, Side.BUY, OrderType.LIMIT, price, quantity, System.nanoTime());
    }

    private Order createSellOrder(String symbol, long price, long quantity) {
        return new Order(idGenerator++, symbol, Side.SELL, OrderType.LIMIT, price, quantity, System.nanoTime());
    }

    // 1. Add BUY order
    @Test
    void testAddBuyOrder_NoMatch() {
        Order buyOrder = createBuyOrder("AAPL", 15000, 100);
        List<Trade> trades = engine.submitOrder(buyOrder).block();
        
        // TODO: Uncomment when matching is implemented
        // assertNotNull(trades);
        // assertTrue(trades.isEmpty());
        // assertEquals(100, buyOrder.getRemainingQuantity());
    }

    // 2. Add SELL order
    @Test
    void testAddSellOrder_NoMatch() {
        Order sellOrder = createSellOrder("AAPL", 15000, 100);
        List<Trade> trades = engine.submitOrder(sellOrder).block();
        
        // TODO: Uncomment when matching is implemented
        // assertNotNull(trades);
        // assertTrue(trades.isEmpty());
        // assertEquals(100, sellOrder.getRemainingQuantity());
    }

    // 3. Orders are sorted correctly by price
    @Test
    void testOrdersSortedCorrectlyByPrice() {
        Order buy1 = createBuyOrder("AAPL", 10000, 10);
        Order buy2 = createBuyOrder("AAPL", 10100, 10);
        Order buy3 = createBuyOrder("AAPL", 9900, 10);

        engine.submitOrder(buy1).block();
        engine.submitOrder(buy2).block();
        engine.submitOrder(buy3).block();

        // TODO: Assert that the order book has buy2 at the top (highest price)
    }

    // 4. Same-price orders preserve FIFO
    @Test
    void testSamePriceOrdersPreserveFIFO() {
        Order buy1 = createBuyOrder("AAPL", 10000, 10);
        Order buy2 = createBuyOrder("AAPL", 10000, 20);

        engine.submitOrder(buy1).block();
        engine.submitOrder(buy2).block();

        Order sell = createSellOrder("AAPL", 10000, 15);
        List<Trade> trades = engine.submitOrder(sell).block();

        // TODO: Assert buy1 is completely filled, and buy2 is partially filled.
    }

    // 5. No match when BUY price < SELL price
    @Test
    void testNoMatchWhenBuyPriceLessThanSellPrice() {
        engine.submitOrder(createSellOrder("AAPL", 10100, 100)).block();
        
        List<Trade> trades = engine.submitOrder(createBuyOrder("AAPL", 10000, 50)).block();
        
        // TODO: Uncomment when implemented
        // assertNotNull(trades);
        // assertTrue(trades.isEmpty());
    }

    // 6. Full fill
    @Test
    void testFullFill() {
        Order buy = createBuyOrder("AAPL", 10000, 100);
        engine.submitOrder(buy).block();

        Order sell = createSellOrder("AAPL", 10000, 100);
        List<Trade> trades = engine.submitOrder(sell).block();

        // TODO: Assert 1 trade of size 100
    }

    // 7. Partial fill
    @Test
    void testPartialFill() {
        Order buy = createBuyOrder("AAPL", 10000, 100);
        engine.submitOrder(buy).block();

        Order sell = createSellOrder("AAPL", 10000, 40);
        List<Trade> trades = engine.submitOrder(sell).block();

        // TODO: Assert 1 trade of size 40
    }

    // 8. Multiple orders at different prices
    @Test
    void testMultipleOrdersAtDifferentPrices() {
        engine.submitOrder(createSellOrder("AAPL", 10100, 50)).block();
        engine.submitOrder(createSellOrder("AAPL", 10200, 50)).block();

        Order aggressiveBuy = createBuyOrder("AAPL", 10300, 75);
        List<Trade> trades = engine.submitOrder(aggressiveBuy).block();

        // TODO: Assert 2 trades (at 10100 and 10200)
    }

    // 9. Multiple orders at the same price
    @Test
    void testMultipleOrdersAtSamePrice() {
        engine.submitOrder(createSellOrder("AAPL", 10000, 30)).block();
        engine.submitOrder(createSellOrder("AAPL", 10000, 40)).block();

        Order buy = createBuyOrder("AAPL", 10000, 50);
        List<Trade> trades = engine.submitOrder(buy).block();

        // TODO: Assert 2 trades (size 30 and 20)
    }

    // 10. Cancellation
    @Test
    void testCancellation() {
        Order buy = createBuyOrder("AAPL", 10000, 100);
        engine.submitOrder(buy).block();
        
        // TODO: Uncomment when implemented
        // Boolean cancelled = engine.cancelOrder("AAPL", buy.getId(), Side.BUY).block();
        // assertTrue(cancelled);
    }

    // 11. Quantity must be positive
    @Test
    void testQuantityMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> 
            new Order(idGenerator++, "AAPL", Side.BUY, OrderType.LIMIT, 10000, 0, System.nanoTime())
        );
    }

    // 12. Price must be positive
    @Test
    void testPriceMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> 
            new Order(idGenerator++, "AAPL", Side.BUY, OrderType.LIMIT, -100, 100, System.nanoTime())
        );
    }
}
