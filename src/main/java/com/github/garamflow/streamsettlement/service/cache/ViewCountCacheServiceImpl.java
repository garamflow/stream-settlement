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
    private static final int BATCH_SIZE = 500;  // Redis SCAN 작업당 처리할 최대 데이터 수
    private static final long VIEW_COUNT_EXPIRE_MINUTES = 5;  // Redis 키의 만료 시간 (분)

    /**
     * Redis Hash 작업을 위한 Operations 객체를 반환합니다.
     * HashOperations를 통해 Redis의 Hash 자료구조에 접근하고 조작할 수 있습니다.
     */
    private HashOperations<String, String, String> getHashOperations() {
        return redisTemplate.opsForHash();
    }

    /**
     * 특정 컨텐츠의 조회수를 1 증가시킵니다.
     * Redis Hash 구조에 contentId를 field로, 조회수를 value로 저장합니다.
     * 키는 5분 후 자동으로 만료되어 메모리를 효율적으로 관리합니다.
     *
     * @param contentId 조회수를 증가시킬 컨텐츠의 ID
     */
    public void incrementViewCount(Long contentId) {
        String key = generateViewCountKey();
        getHashOperations().increment(key, String.valueOf(contentId), 1L);
        redisTemplate.expire(key, VIEW_COUNT_EXPIRE_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * 이전 1분 동안의 컨텐츠별 조회수를 조회합니다.
     * Redis SCAN 명령어를 사용하여 메모리 효율적으로 대량의 데이터를 조회합니다.
     * 오류 발생 시 빈 Map을 반환하여 시스템 안정성을 보장합니다.
     *
     * @param timeWindowKey 조회할 시간대의 Redis 키
     * @return 컨텐츠ID를 key로, 조회수를 value로 하는 Map
     */
    public Map<Long, Long> fetchPreviousMinuteViewCounts(String timeWindowKey) {
        try {
            HashOperations<String, String, String> hashOps = getHashOperations();
            ScanOptions options = ScanOptions.scanOptions()
                    .match("*")
                    .count(BATCH_SIZE)
                    .build();

            Map<Long, Long> contentViewCount = new HashMap<>();
            try (Cursor<Map.Entry<String, String>> cursor = hashOps.scan(timeWindowKey, options)) {
                while (cursor.hasNext()) {
                    Map.Entry<String, String> entry = cursor.next();
                    contentViewCount.put(
                            Long.parseLong(entry.getKey()),
                            Long.parseLong(entry.getValue())
                    );
                }
            }
            return contentViewCount;
        } catch (Exception e) {
            log.error("Failed to fetch view counts for key {}: {}", timeWindowKey, e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * 처리가 완료된 Redis 키를 삭제합니다.
     *
     * @param key 삭제할 Redis 키
     */
    public void deleteProcessedKeys(String key) {
        redisTemplate.delete(key);
    }

    /**
     * 현재 시간을 기준으로 Redis 키를 생성합니다.
     *
     * @return 형식: "content:viewCount:time:yyyy-MM-dd'T'HHmm"
     */
    public String generateViewCountKey() {
        return generateViewCountKey(LocalDateTime.now());
    }

    /**
     * 이전 1분의 시간을 기준으로 Redis 키를 생성합니다.
     *
     * @return 형식: "content:viewCount:time:yyyy-MM-dd'T'HHmm"
     */
    public String generatePreviousMinuteViewCountKey() {
        return generateViewCountKey(LocalDateTime.now().minusMinutes(1));
    }

    /**
     * 주어진 시간을 기준으로 Redis 키를 생성합니다.
     *
     * @param time 키 생성의 기준이 되는 시간
     * @return Redis 키
     */
    private String generateViewCountKey(LocalDateTime time) {
        return VIEW_COUNT_KEY_PREFIX + getTimeWindow(time);
    }

    /**
     * 주어진 시간을 분 단위로 절삭하고 지정된 형식의 문자열로 변환합니다.
     *
     * @param localDateTime 변환할 시간
     * @return 형식: "yyyy-MM-dd'T'HHmm"
     */
    private static String getTimeWindow(LocalDateTime localDateTime) {
        return localDateTime.truncatedTo(ChronoUnit.MINUTES)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmm"));
    }

    /**
     * 모든 조회수 관련 Redis 키를 삭제합니다.
     * 주로 테스트나 초기화 용도로 사용됩니다.
     */
    public void flushAllViewCounts() {
        Set<String> keys = redisTemplate.keys(VIEW_COUNT_KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}