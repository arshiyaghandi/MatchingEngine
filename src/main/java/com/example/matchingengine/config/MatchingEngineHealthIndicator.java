package com.example.matchingengine.config;

import com.example.matchingengine.service.MatchingEngineService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Custom health indicator that reports the operational status of the
 * matching engine, including the number of active order books and
 * total orders across all symbols.
 *
 * <p>Visible at {@code /actuator/health} under the key
 * {@code matchingEngine}.</p>
 */
@Component
public class MatchingEngineHealthIndicator implements HealthIndicator {

    private final MatchingEngineService service;

    public MatchingEngineHealthIndicator(MatchingEngineService service) {
        this.service = service;
    }

    @Override
    public Health health() {
        try {
            var engine = service.getEngine();
            return Health.up()
                    .withDetail("status", "RUNNING")
                    .withDetail("description", "Matching engine is operational")
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("status", "DOWN")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
