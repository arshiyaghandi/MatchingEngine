-- ═══════════════════════════════════════════════════════════
-- V2: Add stop_price column for stop orders
-- ═══════════════════════════════════════════════════════════

ALTER TABLE orders ADD COLUMN IF NOT EXISTS stop_price BIGINT DEFAULT 0;
