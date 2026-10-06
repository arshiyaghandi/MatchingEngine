-- ═══════════════════════════════════════════════════════════
-- Matching Engine Schema
-- Compatible with PostgreSQL 16+ and H2 (for tests)
-- ═══════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS orders (
    id                 BIGINT       PRIMARY KEY,
    symbol             VARCHAR(20)  NOT NULL,
    side               VARCHAR(4)   NOT NULL,
    type               VARCHAR(10)  NOT NULL,
    price              BIGINT       NOT NULL,
    quantity           BIGINT       NOT NULL,
    remaining_quantity BIGINT       NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS trades (
    id             BIGINT       PRIMARY KEY,
    buy_order_id   BIGINT       NOT NULL,
    sell_order_id  BIGINT       NOT NULL,
    symbol         VARCHAR(20)  NOT NULL,
    price          BIGINT       NOT NULL,
    quantity       BIGINT       NOT NULL,
    executed_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_orders_symbol ON orders(symbol);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_trades_symbol ON trades(symbol);
CREATE INDEX IF NOT EXISTS idx_trades_buy_order ON trades(buy_order_id);
CREATE INDEX IF NOT EXISTS idx_trades_sell_order ON trades(sell_order_id);
