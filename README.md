# Matching Engine

A production-oriented stock exchange **Matching Engine** built with Java 21, Spring WebFlux, and Project Reactor.

## Architecture

```text
                    ┌─────────────────────────────────────────────┐
                    │              Matching Engine                 │
                    │                                             │
  ┌──────────┐     │  ┌───────────┐    ┌──────────────────┐      │     ┌──────────┐
  │  Client   │────▶│  │  WebFlux  │───▶│  MatchingEngine  │──────│────▶│  Kafka   │
  │ (REST)    │◀────│  │  Router   │◀───│  (Core Service)  │      │     │ (Trades) │
  └──────────┘     │  └───────────┘    └────────┬─────────┘      │     └──────────┘
                    │                           │                 │
  ┌──────────┐     │                    ┌───────▼───────┐         │
  │  Kafka   │────▶│                    │   OrderBook   │         │
  │ (Orders) │     │                    │  (TreeMap)    │         │
  └──────────┘     │                    └───────┬───────┘         │
                    │              ┌─────────────┼─────────────┐  │
                    │              │             │             │  │
                    │        ┌─────▼─────┐ ┌────▼────┐  ┌─────▼──┐
                    │        │PostgreSQL │ │  Redis  │  │ Redis  │
                    │        │ (R2DBC)   │ │ (Cache) │  │(Events)│
                    │        │Final State│ │Snapshot │  │Daily   │
                    │        └───────────┘ └─────────┘  └────────┘
                    └─────────────────────────────────────────────┘
```

## Concepts

### What is a Matching Engine?
The core software of a stock exchange. It takes incoming buy and sell orders, and matches them fairly and deterministically using **Price-Time Priority**.

### Order Book
An electronic list of buy (BID) and sell (ASK) orders for a specific instrument, organised by price level.

```text
             ORDER BOOK  (AAPL)

        ASK (sellers)
─────────────────────────────────
 101.00 │ Order#7            
 100.50 │ Order#3            
 100.00 │ Order#9            
─────────────────────────────────
  99.50 │ Order#4            
  99.00 │ Order#1            
  98.50 │ Order#2            
        BID (buyers)
```

### Price-Time Priority
1. **Price**: Better prices execute first. Higher bids and lower asks have priority.
2. **Time (FIFO)**: At the same price, the order that arrived first matches first.

### Execution Price
The **resting order's price** is always used as the execution price. If a buy at 101 matches a resting sell at 100, the trade executes at 100.

### Integer Price Representation
Prices use `long` with a fixed-point scale of **100** (2 decimal places):

| Human Price | Stored Value |
|-------------|-------------|
| 150.25      | 15025       |
| 35,120.50   | 3512050     |
| 99.00       | 9900        |

**Why not `double`?** Floating-point arithmetic introduces precision loss (`0.1 + 0.2 ≠ 0.3`). Financial systems require exact precision.

## Tech Stack

| Component     | Technology                       |
|---------------|----------------------------------|
| Language      | Java 21                          |
| Web           | Spring WebFlux (Functional)      |
| Reactive      | Project Reactor                  |
| Database      | PostgreSQL 16 (R2DBC)            |
| Cache         | Redis 7 (Reactive Lettuce)       |
| Messaging     | Apache Kafka (Reactor Kafka)     |
| Testing       | JUnit 5, WebTestClient, StepVerifier |
| Containers    | Docker Compose                   |

## Data Flow

```text
1. Order arrives via REST API or Kafka topic "orders.inbound"
2. MatchingEngineService.submitOrder(order)
3. OrderBook.addOrder(order) ─── matching algorithm ─── List<Trade>
4. Side effects (all non-blocking, fault-tolerant):
   ├── PostgreSQL: persist final order/trade state
   ├── Kafka:      publish trades to "trades.outbound"
   ├── Redis:      update order-book cache (write-through)
   └── Redis:      append to daily event log (TTL = 7 days)
```

## REST API

### Submit Order
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"symbol":"AAPL","side":"BUY","type":"LIMIT","price":15025,"quantity":100}'
```

**Response:**
```json
{
  "orderId": 1,
  "symbol": "AAPL",
  "side": "BUY",
  "price": 15025,
  "quantity": 100,
  "trades": []
}
```

### Cancel Order
```bash
curl -X DELETE http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"symbol":"AAPL","orderId":1,"side":"BUY"}'
```

### Get Order Book
```bash
curl http://localhost:8080/api/orderbook/AAPL
```

**Response:**
```json
{
  "symbol": "AAPL",
  "bids": [{"price": 15000, "totalQuantity": 200, "orderCount": 2}],
  "asks": [{"price": 15100, "totalQuantity": 100, "orderCount": 1}]
}
```

## Kafka Topics

| Topic              | Direction | Payload                |
|--------------------|-----------|------------------------|
| `orders.inbound`   | Consumer  | `OrderRequest` (JSON)  |
| `trades.outbound`  | Producer  | Trade event (JSON)     |

## Redis Keys

| Key Pattern                     | Type  | Description                    | TTL      |
|---------------------------------|-------|--------------------------------|----------|
| `orderbook:{symbol}`            | String| JSON order-book snapshot       | None     |
| `events:orders:{yyyy-MM-dd}`    | List  | Daily event log entries        | 7 days   |

## Quick Start

### 1. Start infrastructure
```bash
docker-compose up -d
```

### 2. Build and run
```bash
mvn clean package -DskipTests
mvn spring-boot:run
```

### 3. Run tests
```bash
mvn clean test
```

Unit tests use H2 in-memory and do not require Docker.

## Project Structure
```
src/main/java/com/example/matchingengine/
├── api/                    # WebFlux handlers & router
│   ├── MatchingEngineHandler.java
│   ├── RouterConfig.java
│   └── GlobalExceptionHandler.java
├── cache/                  # Redis services
│   ├── OrderBookCacheService.java
│   └── OrderEventLogService.java
├── config/                 # Spring configuration
│   ├── DatabaseConfig.java
│   ├── KafkaConfig.java
│   └── RedisConfig.java
├── domain/                 # Core domain model
│   ├── Order.java
│   ├── Trade.java
│   ├── PriceLevel.java
│   ├── Side.java
│   └── OrderType.java
├── dto/                    # Request/Response DTOs
├── engine/                 # Pure matching engine (no Spring)
│   ├── MatchingEngine.java
│   └── OrderBook.java
├── exception/
├── kafka/                  # Reactor Kafka producer/consumer
├── persistence/            # R2DBC entities & repositories
└── service/
    └── MatchingEngineService.java  # Reactive orchestrator
```

## Design Decisions

- **Core engine has zero Spring dependencies** — `MatchingEngine` and `OrderBook` are pure Java, directly unit-testable.
- **Per-symbol locking** — Different symbols process in parallel; same symbol serialised via `synchronized(orderBook)`.
- **Fault-tolerant side effects** — If PostgreSQL, Redis, or Kafka is down, matching still succeeds. Side effects use `onErrorResume(Mono.empty())`.
- **Optional infrastructure** — All infra beans use `@ConditionalOnProperty`. The engine runs in-memory without any external dependencies.

## Current Limitations
- Only LIMIT orders (no Market, Stop, IOC, FOK)
- Single JVM (no clustering / leader election)
- No authentication / authorisation
- No WebSocket streaming (yet)
- Schema managed via `CREATE IF NOT EXISTS` (no Flyway/Liquibase)

## Future Roadmap
1. Market Orders, IOC, FOK, Stop Orders
2. WebSocket streaming for real-time order-book updates
3. Flyway database migrations
4. Distributed matching with leader election
5. Object pooling & GC optimisation for ultra-low latency
6. Prometheus metrics & Grafana dashboards
