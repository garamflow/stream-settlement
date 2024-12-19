package com.github.garamflow.streamsettlement.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 클라이언트 설정
 * - 분산 락 구현을 위한 Redisson 설정
 * - Redis 서버 연결 및 클라이언트 풀 관리
 */
@Configuration
public class RedissonConfig {
    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    private static final String REDISSON_URL_PREFIX = "redis://";

    /**
     * Redisson 클라이언트 설정
     * - 단일 서버 모드 사용
     * - 구독 연결 풀 크기 최적화
     */
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        String redisAddress = REDISSON_URL_PREFIX + redisHost + ":" + redisPort;
        config.useSingleServer()
                .setAddress(redisAddress)
                .setSubscriptionConnectionMinimumIdleSize(1)
                .setSubscriptionConnectionPoolSize(2);
        return Redisson.create(config);
    }
} 