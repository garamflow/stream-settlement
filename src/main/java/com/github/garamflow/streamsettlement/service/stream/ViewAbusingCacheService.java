package com.github.garamflow.streamsettlement.service.stream;

import com.github.garamflow.streamsettlement.exception.CacheOperationException;
import com.github.garamflow.streamsettlement.redis.dto.AbusingKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.github.garamflow.streamsettlement.redis.constant.RedisKeyConstants.ABUSE_KEY_PREFIX;
import static com.github.garamflow.streamsettlement.redis.constant.RedisKeyConstants.LOCK_PREFIX;

@Slf4j
@Service
@RequiredArgsConstructor
public class ViewAbusingCacheService {
    private final RedissonClient redissonClient;
    private final RedisTemplate<String, String> redisTemplate;
    private static final Duration ABUSE_WINDOW = Duration.ofSeconds(30);
    private static final Duration LOCK_WAIT_TIME = Duration.ofMillis(500);
    private static final Duration LOCK_LEASE_TIME = Duration.ofSeconds(1);

    public boolean isAbusing(AbusingKey key) {
        String lockKey = generateLockKey(key);
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 락 획득 실패시 기본적으로 어뷰징으로 간주하지 않음
            if (!lock.tryLock(LOCK_WAIT_TIME.toMillis(), LOCK_LEASE_TIME.toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("Failed to acquire lock for abuse check: {}", key);
                return false;
            }

            try {
                String abuseKey = generateAbuseKey(key);
                boolean isCreator = key.memberId().equals(key.creatorId());
                boolean hasAbuseRecord = Boolean.TRUE.equals(redisTemplate.hasKey(abuseKey));

                return isCreator || hasAbuseRecord;
            } finally {
                lock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lock acquisition interrupted for key: {}", key, e);
            return false;
        } catch (Exception e) {
            log.error("Error checking abuse status for key: {}", key, e);
            return false;
        }
    }

    public void setAbusing(AbusingKey key) {
        try {
            String abuseKey = generateAbuseKey(key);
            redisTemplate.opsForValue().set(
                    abuseKey,
                    "1",
                    ABUSE_WINDOW
            );
        } catch (Exception e) {
            log.error("Failed to set abuse record for key: {}", key, e);
            throw new CacheOperationException("Failed to set abuse record", e);
        }
    }

    public void recordView(AbusingKey key) {
        setAbusing(key);
    }

    private String generateLockKey(AbusingKey key) {
        return String.format("%s:lock:content:%d:member:%d:ip:%s",
                LOCK_PREFIX,
                key.contentId(),
                key.memberId(),
                key.ip()
        );
    }

    private String generateAbuseKey(AbusingKey key) {
        return String.format("%s:content:%d:member:%d:ip:%s",
                ABUSE_KEY_PREFIX,
                key.contentId(),
                key.memberId(),
                key.ip()
        );
    }
}
