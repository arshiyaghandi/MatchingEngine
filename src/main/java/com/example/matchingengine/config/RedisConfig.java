package com.example.matchingengine.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration — only active when {@code matching-engine.redis.enabled=true}.
 *
 * <p>Provides a {@link ReactiveRedisTemplate} with String keys and JSON values.</p>
 */
@Configuration
@ConditionalOnProperty(name = "matching-engine.redis.enabled", havingValue = "true", matchIfMissing = false)
public class RedisConfig {

    @Bean
    @Primary
    public ReactiveRedisTemplate<String, String> reactiveJsonRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {

        var keySerializer = new StringRedisSerializer();
        var valueSerializer = new StringRedisSerializer();

        RedisSerializationContext<String, String> context =
                RedisSerializationContext.<String, String>newSerializationContext(keySerializer)
                        .value(valueSerializer)
                        .hashKey(keySerializer)
                        .hashValue(valueSerializer)
                        .build();

        return new ReactiveRedisTemplate<>(connectionFactory, context);
    }
}
