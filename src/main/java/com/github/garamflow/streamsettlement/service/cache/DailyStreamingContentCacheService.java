package com.github.garamflow.streamsettlement.service.cache;

import com.github.garamflow.streamsettlement.exception.CacheOperationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.github.garamflow.streamsettlement.redis.constant.RedisKeyConstants.DAILY_VIEWED_CONTENT_KEY_PREFIX;
import static com.github.garamflow.streamsettlement.redis.constant.RedisKeyConstants.LOCK_PREFIX;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyStreamingContentCacheService {

    private final RedisTemplate<String, String> redisTemplate;
    private final RedissonClient redissonClient;

    private static final Duration LOCK_WAIT_TIME = Duration.ofMillis(500);
    private static final Duration LOCK_LEASE_TIME = Duration.ofSeconds(1);

    public Boolean isExistContentId(Long contentId) {
        String lockKey = generateLockKey(contentId);
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (!lock.tryLock(500, TimeUnit.MILLISECONDS)) {
                return true;
            }

            try {
                String key = generateDailyStreamingContentKey();
                return redisTemplate.opsForSet().isMember(key, String.valueOf(contentId));
            } finally {
                lock.unlock();
            }
        } catch (InterruptedException e) {
            log.error("Lock acquisition failed for contentPostId: {}", contentId, e);
            return true;
        }
    }

    public void setContentId(Long contentId) {
        LocalDate today = LocalDate.now();
        try {
            setContentIdWithDate(contentId, today);
            log.debug("Set content ID {} for date {}", contentId, today);
        } catch (Exception e) {
            log.error("Failed to set content ID {} for date {}", contentId, today, e);
            throw new CacheOperationException("Failed to set content ID", e);
        }
    }

    private String generateDailyStreamingContentKey() {
        return DAILY_VIEWED_CONTENT_KEY_PREFIX + LocalDate.now();
    }

    private String generateLockKey(Long contentId) {
        return String.format("%s:%s:%d", LOCK_PREFIX, LocalDate.now(), contentId);
    }

    public Set<Long> getPreviousDayStreamingContents() {
        String previousDayKey = DAILY_VIEWED_CONTENT_KEY_PREFIX + LocalDate.now().minusDays(1);
        Set<String> contentIds = redisTemplate.opsForSet().members(previousDayKey);
        return contentIds != null ? contentIds.stream()
                .map(Long::parseLong)
                .collect(Collectors.toSet())
                : Collections.emptySet();
    }

    public void setContentIdWithDate(Long contentId, LocalDate date) {
        String lockKey = generateLockKey(contentId);
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (!lock.tryLock(LOCK_WAIT_TIME.toMillis(), LOCK_LEASE_TIME.toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("Failed to acquire lock for content: {}", contentId);
                return;
            }

            try {
                String key = generateDailyKey(date);
                redisTemplate.opsForSet().add(key, contentId.toString());

                // 다음날 새벽 4시까지 유효
                LocalDateTime expiryTime = date.plusDays(1).atTime(4, 0);
                Duration timeUntilExpiry = Duration.between(LocalDateTime.now(), expiryTime);
                redisTemplate.expire(key, timeUntilExpiry.toSeconds(), TimeUnit.SECONDS);

                log.debug("Set content {} with expiry at {}", contentId, expiryTime);
            } finally {
                lock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lock acquisition interrupted for content: {}", contentId, e);
            throw new CacheOperationException("Lock acquisition interrupted", e);
        }
    }

    public Set<Long> getContentIdsByDate(LocalDate date) {
        String key = generateDailyKey(date);
        Set<String> contentIds = redisTemplate.opsForSet().members(key);
        return contentIds != null ? contentIds.stream()
                .map(Long::parseLong)
                .collect(Collectors.toSet())
                : Collections.emptySet();
    }

    private String generateDailyKey(LocalDate date) {
        return DAILY_VIEWED_CONTENT_KEY_PREFIX + date;
    }
} 