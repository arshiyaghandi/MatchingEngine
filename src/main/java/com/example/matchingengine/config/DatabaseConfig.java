package com.example.matchingengine.config;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;

/**
 * Initialises the R2DBC database with {@code schema.sql} on startup.
 *
 * <p>Uses {@code CREATE TABLE IF NOT EXISTS} so it is safe to run on every
 * start against both PostgreSQL (production) and H2 (tests).</p>
 *
 * <p>This initializer is disabled when Flyway is enabled, as Flyway
 * manages schema migrations instead.</p>
 */
@Configuration
public class DatabaseConfig {

    @Bean
    @ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "false", matchIfMissing = true)
    ConnectionFactoryInitializer databaseInitializer(ConnectionFactory connectionFactory) {
        var initializer = new ConnectionFactoryInitializer();
        initializer.setConnectionFactory(connectionFactory);
        initializer.setDatabasePopulator(
                new ResourceDatabasePopulator(new ClassPathResource("schema.sql")));
        return initializer;
    }
}
