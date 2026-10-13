package com.example.matchingengine.service;

import org.springframework.stereotype.Service;

/**
 * Snowflake-inspired unique ID generator for orders and trades.
 *
 * <h3>ID Layout (64 bits)</h3>
 * <pre>
 *  ┌──────────────────────────┬──────────────┐
 *  │  timestamp (ms) - epoch  │  sequence    │
 *  │        52 bits           │   12 bits    │
 *  └──────────────────────────┴──────────────┘
 * </pre>
 *
 * <p>Produces <strong>monotonically increasing</strong>, time-ordered IDs
 * that are unique across JVM restarts. Up to 4096 IDs per millisecond.</p>
 *
 * <p><strong>Thread-safety:</strong> All public methods are synchronized.</p>
 */
@Service
public class IdGeneratorService {

    /** Custom epoch: 2024-01-01T00:00:00Z */
    private static final long CUSTOM_EPOCH = 1_704_067_200_000L;

    private static final int SEQUENCE_BITS = 12;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1; // 4095

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    /**
     * Generates the next unique ID.
     *
     * @return a positive, monotonically increasing 64-bit ID
     */
    public synchronized long nextId() {
        long currentTimestamp = System.currentTimeMillis() - CUSTOM_EPOCH;

        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // Sequence exhausted within this millisecond — spin-wait
                currentTimestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0;
        }

        lastTimestamp = currentTimestamp;
        return (currentTimestamp << SEQUENCE_BITS) | sequence;
    }

    private long waitNextMillis(long lastTs) {
        long ts;
        do {
            ts = System.currentTimeMillis() - CUSTOM_EPOCH;
        } while (ts <= lastTs);
        return ts;
    }
}
