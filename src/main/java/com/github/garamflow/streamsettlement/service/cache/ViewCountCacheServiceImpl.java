package com.github.garamflow.streamsettlement.service.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.github.garamflow.streamsettlement.redis.constant.RedisKeyConstants.VIEW_COUNT_KEY_PREFIX;

@Slf4j
@Service
@RequiredArgsConstructor
public class ViewCountCacheServiceImpl implements ViewCountCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final int BATCH_SIZE = 500;

    /**
     * key : content:viewCount:time:202410271557
     */
    public void incrementViewCount(Long contentId) {
        String key = generateViewCountKey();

        // contentId가 없으면 자동으로 0으로 초기화한 후 1 증가, 있으면 기존 값에 1 증가
        HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
        hashOps.increment(key, String.valueOf(contentId), 1L);

        redisTemplate.expire(key, 5, TimeUnit.MINUTES);
    }

    /**
     * Redis 에서 이전 1분간의 영상별 조회수를 조회합니다.
     * https://redis.io/docs/latest/commands/scan/
     */
    public Map<Long, Long> fetchPreviousMinuteViewCounts(String timeWindowKey) {
        HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
        ScanOptions options = ScanOptions.scanOptions()
                .match("*")
                .count(BATCH_SIZE)
                .build();
        Cursor<Map.Entry<String, String>> cursor = hashOps.scan(timeWindowKey, options);

        Map<Long, Long> contentViewCount = new HashMap<>();

        while (cursor.hasNext()) {
            Map.Entry<String, String> entry = cursor.next();
            contentViewCount.put(Long.parseLong(entry.getKey()), Long.parseLong(entry.getValue()));
        }

        return contentViewCount;
    }

    public void deleteProcessedKeys(String key) {
        redisTemplate.delete(key);
    }

    /**
     * 직전 1분 동안의 조회수를 조회하기 위한 key 를 생성합니다.
     */
    public String generatePreviousMinuteViewCountKey() {
        return VIEW_COUNT_KEY_PREFIX + getTimeWindow(LocalDateTime.now().minusMinutes(1));
    }

    /**
     * 현재 시간에서 초 단위를 절삭 후 Key 를 생성합니다
     */
    @Override
    public String generateViewCountKey() {
        return VIEW_COUNT_KEY_PREFIX + getTimeWindow(LocalDateTime.now());
    }

    private static String getTimeWindow(LocalDateTime localDateTime) {
        return localDateTime.truncatedTo(ChronoUnit.MINUTES)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmm"));
    }

    @Override
    public void flushAllViewCounts() {
        Set<String> keys = redisTemplate.keys(VIEW_COUNT_KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}