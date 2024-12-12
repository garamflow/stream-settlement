package com.github.garamflow.streamsettlement.service;

import com.github.garamflow.streamsettlement.batch.TestDataGenerator;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import com.github.garamflow.streamsettlement.redis.dto.AbusingKey;
import com.github.garamflow.streamsettlement.repository.stream.ContentPostRepository;
import com.github.garamflow.streamsettlement.scheduler.ViewCountSyncScheduler;
import com.github.garamflow.streamsettlement.service.cache.ViewCountCacheService;
import com.github.garamflow.streamsettlement.service.stream.StreamingService;
import com.github.garamflow.streamsettlement.service.stream.ViewAbusingCacheService;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.github.garamflow.streamsettlement.redis.constant.RedisKeyConstants.VIEW_COUNT_KEY_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)  // 추가
@Slf4j
class StreamingServiceTest {

    @Autowired
    private StreamingService streamingService;

    @Autowired
    private TestDataGenerator testDataGenerator;

    @Autowired
    private ContentPostRepository contentPostRepository;

    @Autowired
    private ViewAbusingCacheService viewAbusingCacheService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ViewCountSyncScheduler viewCountSyncScheduler;

    @Autowired
    private ViewCountCacheService viewCountCacheService;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void init() {
        // Redis 초기화
        Objects.requireNonNull(redisTemplate.getConnectionFactory())
                .getConnection()
                .serverCommands()
                .flushAll();

        // TestDataGenerator가 이미 @Transactional을 가지고 있으므로
        // 직접 호출만 하면 됨
        testDataGenerator.createTestData(
                10,    // 10명의 사용자
                5,     // 5개의 컨텐츠
                3,     // 3개의 광고
                0      // 시청 로그는 생성하지 않음
        );
    }

    @Test
    @Order(1)
    @Transactional
    @DisplayName("4. 조회수 증가 테스트")
    void viewCountIncrementTest() throws InterruptedException {
        // given
        List<ContentPost> allPosts = contentPostRepository.findAll();
        log.info("전체 컨텐츠 개수: {}", allPosts.size());

        ContentPost contentPost = allPosts.get(0);
        Long contentPostId = contentPost.getId();
        Long memberId = 1L;

        log.info("테스트 시작 - contentPostId: {}, memberId: {}", contentPostId, memberId);
        log.info("시작 전 컨텐츠 상태: {}", contentPost);
        log.info("시작 전 컨텐츠 조회수: {}", contentPost.getTotalViews());

        String currentKey = VIEW_COUNT_KEY_PREFIX + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmm"));

        // 직접 Redis에 데이터 추가
        viewCountCacheService.incrementViewCount(contentPostId);

        Map<Long, Long> currentCounts = viewCountCacheService.fetchPreviousMinuteViewCounts(currentKey);
        log.info("수동 증가 후 Redis 데이터: {}", currentCounts);

        // when
        streamingService.startPlayback(memberId, contentPostId);
        log.info("시청 시작 요청 완료");

        // SQL 쿼리 직접 실행
        String sql = """
                UPDATE content_post
                SET total_views = total_views + 1
                WHERE content_post_id = :contentId
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("contentId", contentPostId);

        int updatedRows = namedParameterJdbcTemplate.update(sql, params);
        log.info("SQL 직접 실행 결과: {} rows updated", updatedRows);

        // SQL로 직접 조회
        String selectSql = """
                SELECT content_post_id, total_views 
                FROM content_post 
                WHERE content_post_id = :contentId
                """;

        Map<String, Object> result = namedParameterJdbcTemplate.queryForMap(selectSql, params);
        log.info("SQL 직접 조회 결과: contentId={}, totalViews={}",
                result.get("content_post_id"), result.get("total_views"));

        // then
        assertThat((Long) result.get("total_views")).isPositive();
    }

    @Test
    @Order(2)
    @Transactional
    @DisplayName("5. 어뷰징 체크 테스트")
    void abusingCheckTest() {
        // given
        Long contentPostId = 1L;
        Long memberId = 2L;  // 컨텐츠 ���성자(1L)가 아닌 다른 사용자
        AbusingKey key = AbusingKey.of(memberId, contentPostId, 1L, "127.0.0.1");

        // when
        streamingService.startPlayback(contentPostId, memberId);

        // then
        await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    boolean isAbusing = viewAbusingCacheService.isAbusing(key);
                    log.info("어뷰징 체크 결과: {}", isAbusing);
                    assertThat(isAbusing).isFalse();
                });
    }

    @Test
    @Order(3)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("6. 조회수 증가 테스트")
    void viewCountIncrementTest2() {
        // given
        ContentPost contentPost = contentPostRepository.findAll().get(0);
        Long contentPostId = contentPost.getId();
        Long memberId = 1L;

        log.info("테스트 시작 - contentPostId: {}, memberId: {}", contentPostId, memberId);
        log.info("시작 전 컨텐츠 조회수: {}", contentPost.getTotalViews());

        // when - Redis에 직접 조회수 증가
        viewCountCacheService.incrementViewCount(contentPostId);

        // DB에도 직접 조회수 증가
        String updateSql = """
        UPDATE content_post 
        SET total_views = total_views + 1 
        WHERE content_post_id = :id
        """;
        namedParameterJdbcTemplate.update(updateSql, Map.of("id", contentPostId));

        // then
        await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    // Redis 조회수 확인
                    String currentKey = viewCountCacheService.generateViewCountKey();
                    Map<Long, Long> viewCounts = viewCountCacheService.fetchPreviousMinuteViewCounts(currentKey);

                    // DB 조회수 확인
                    String selectSql = """
                SELECT total_views FROM content_post 
                WHERE content_post_id = :id
                """;
                    Integer dbViews = namedParameterJdbcTemplate
                            .queryForObject(selectSql, Map.of("id", contentPostId), Integer.class);

                    log.info("조회수 확인 - Redis key: {}, Redis counts: {}, DB views: {}",
                            currentKey, viewCounts, dbViews);

                    // Redis와 DB 모두에서 조회수가 증가했는지 확인
                    assertThat(dbViews)
                            .as("DB view count should be positive")
                            .isPositive();

                    assertThat(viewCounts)
                            .as("Redis should contain view count for content %d", contentPostId)
                            .containsKey(contentPostId);

                    assertThat(viewCounts.get(contentPostId))
                            .as("Redis view count for content %d should be positive", contentPostId)
                            .isPositive();
                });
    }

    @Test
    @Order(4)
    @DisplayName("7. 배치 동기화 테스트")
    void batchSyncTest() {
        // given
        ContentPost contentPost = contentPostRepository.findAll().get(0);
        Long contentPostId = contentPost.getId();

        // 현재 시간 기준으로 이전 분의 키 생성
        String timeWindowKey = viewCountCacheService.generatePreviousMinuteViewCountKey();

        // Redis에 직접 이전 분의 데이터 추가
        redisTemplate.opsForHash().put(timeWindowKey, String.valueOf(contentPostId), "1");

        Map<Long, Long> initialRedisData = viewCountCacheService.fetchPreviousMinuteViewCounts(timeWindowKey);
        log.info("Initial Redis data for key {}: {}", timeWindowKey, initialRedisData);

        // when
        viewCountSyncScheduler.syncContentViewCountsToDatabase();

        // then
        await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    // DB 조회수 확인
                    String selectSql = """
                SELECT total_views FROM content_post 
                WHERE content_post_id = :id
                """;
                    Long dbViews = namedParameterJdbcTemplate
                            .queryForObject(selectSql, Map.of("id", contentPostId), Long.class);

                    // Redis 키가 삭제되었는지 확인
                    Boolean keyExists = redisTemplate.hasKey(timeWindowKey);

                    log.info("Final state - DB views: {}, Redis key exists: {}", dbViews, keyExists);

                    assertThat(dbViews)
                            .as("DB view count should be 1")
                            .isEqualTo(1L);

                    assertThat(keyExists)
                            .as("Redis key should be deleted after sync")
                            .isFalse();
                });
    }
} 