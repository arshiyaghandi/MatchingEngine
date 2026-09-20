# Matching Engine

This project contains the core of a low-latency, production-oriented stock exchange Matching Engine. 
It focuses purely on the matching algorithm and order book management, keeping dependencies strictly minimal.

## Concepts

### 1. What is a Matching Engine?
A Matching Engine is the core software of an exchange. It keeps track of all open orders (offers to buy or sell) and matches them when conditions are met. It ensures that trades are executed fairly and deterministically based on defined rules.

### 2. What is an Order Book?
An Order Book is an electronic list of buy and sell orders for a specific financial instrument (e.g., a stock like AAPL), organized by price level. It consists of two sides: BID (buyers) and ASK (sellers).

### 3. Bid / Ask
- **BID (Buy Side):** Represents orders from buyers. Highest prices sit at the top of the bid queue.
- **ASK (Sell Side):** Represents orders from sellers. Lowest prices sit at the top of the ask queue.

```text
             ORDER BOOK

        ASK
-------------------------
101.00 | Order 7
100.50 | Order 3
100.00 | Order 9
-------------------------
 99.50 | Order 4
 99.00 | Order 1
 98.50 | Order 2
        BID
```

### 4. Price-Time Priority
To ensure fairness, orders are sorted based on two main criteria:
1. **Price:** Better prices are executed first. For Bids, higher is better. For Asks, lower is better.
2. **Time:** If multiple orders have the same price, the order that arrived first (FIFO) gets executed first.

### 5. Limit Orders
A Limit Order specifies the maximum price a buyer is willing to pay, or the minimum price a seller is willing to accept.

### 6. Partial Fill
When an incoming order matches with a resting order, but the resting order's quantity is smaller than the incoming order's quantity, the incoming order will be "partially filled". The remainder either continues matching against other resting orders or is added to the book.

### 7. Full Fill
When an order's requested quantity is completely matched and filled.

### 8. Cancellation
Orders can be cancelled before they are fully filled. A cancelled order is removed from the book.

### 9. Integer Price Representation
Prices in this system are represented as integers (e.g., `long`), not as floating-point numbers (`float` or `double`).
**Why?**
Floating-point arithmetic introduces precision loss (e.g., `0.1 + 0.2 = 0.30000000000000004`). In financial systems, exact precision is mandatory. We use integer fixed-point representations. For example, with a scale of 2 (cents), a price of `35,120.50` is stored as `3512050`.

## Current Architecture & Limitations
- **Pure Java 21:** No Spring, no REST APIs, no database layer.
- **Data Structures:** Basic abstraction is provided. Efficient execution maps/trees are left to the implementation.
- **No Persistence/Messaging:** Memory-only for now, skipping Kafka/Redis.
- **Single Threaded Context:** Currently assumes a deterministic single-threaded or appropriately locked environment for matching.

## Future Roadmap
1. Support for Market Orders, Stop Orders, IOC (Immediate Or Cancel), FOK (Fill Or Kill).
2. Advanced optimizations (Object pooling, avoiding GC pauses).
3. Integration with a messaging queue (Disruptor / Kafka) to handle high-throughput order ingestion.

## Engineering Constraints
- Avoid `BigDecimal` for prices; use primitives (`long`) to avoid object overhead.
- Avoid unnecessary object creation in the hot path.
- Avoid Streams API inside the matching loop.

## Setup & Testing
This project uses Java 21 and Maven. To run tests:
```bash
mvn clean test
```
