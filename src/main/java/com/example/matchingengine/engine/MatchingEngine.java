package com.example.matchingengine.engine;

import com.example.matchingengine.domain.Order;
import com.example.matchingengine.domain.Side;
import com.example.matchingengine.domain.Trade;
import com.example.matchingengine.exception.InvalidOrderException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Service
public class MatchingEngine {
    
    private final Map<String, OrderBook> orderBooks;
    
    public MatchingEngine() {
        this.orderBooks = new ConcurrentHashMap<>();
    }

    /**
     * Submits an order to the matching engine.
     * Reactively returns the list of trades resulting from this order.
     * 
     * @param order the order to be processed
     * @return a Mono emitting a list of trades resulting from this order
     * @throws InvalidOrderException if the order is invalid (e.g. duplicate ID, negative price, etc.)
     */
    public Mono<List<Trade>> submitOrder(Order order) {
        return Mono.fromCallable(() -> {
            validateOrder(order);
            
            OrderBook orderBook = orderBooks.computeIfAbsent(order.getSymbol(), OrderBook::new);
            
            // Note: Since this is a core engine, we synchronize access per OrderBook 
            // to ensure deterministic sequential processing of the matching logic.
            synchronized (orderBook) {
                return orderBook.addOrder(order);
            }
        });
    }

    /**
     * Cancels an existing order.
     * 
     * @param symbol the symbol of the orderbook
     * @param orderId the order to cancel
     * @param side the side of the order
     * @return a Mono emitting true if successfully cancelled, false if not found
     */
    public Mono<Boolean> cancelOrder(String symbol, long orderId, Side side) {
        return Mono.fromCallable(() -> {
            OrderBook orderBook = orderBooks.get(symbol);
            if (orderBook != null) {
                synchronized (orderBook) {
                    return orderBook.cancelOrder(orderId, side);
                }
            }
            return false;
        });
    }

    private void validateOrder(Order order) {
        // Validation for price, quantity, etc. is already handled in the Order constructor.
    }
    
    public OrderBook getOrderBook(String symbol) {
        return orderBooks.get(symbol);
    }
}
