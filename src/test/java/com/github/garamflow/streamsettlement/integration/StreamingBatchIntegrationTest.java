package com.github.garamflow.streamsettlement.integration;

import com.github.garamflow.streamsettlement.batch.TestDataGenerator;
import com.github.garamflow.streamsettlement.entity.settlement.Settlement;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import com.github.garamflow.streamsettlement.entity.stream.Log.DailyWatchedContent;
import com.github.garamflow.streamsettlement.repository.log.DailyWatchedContentRepository;
import com.github.garamflow.streamsettlement.repository.log.MemberContentWatchLogRepository;
import com.github.garamflow.streamsettlement.repository.settlement.SettlementRepository;
import com.github.garamflow.streamsettlement.repository.statistics.ContentStatisticsRepository;
import com.github.garamflow.streamsettlement.repository.stream.ContentPostRepository;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBatchTest
@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "spring.task.execution.enabled=false",
        "scheduler.enabled=false",
        "spring.main.allow-scheduling=false",
        "spring.main.allow-bean-definition-overriding=true",
        "view-count.sync.enabled=false",
})
@Import(TestConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Slf4j
class StreamingBatchIntegrationTest {

    private static final int CONTENT_COUNT = 3;  // 테스트할 콘텐츠 수
    private static final int MEMBER_COUNT = 10;  // 각 콘텐츠당 시청자 수
    private static final int BATCH_SIZE = 1000;  // 클래스 상단에 추가

    // 중규모 테스트 상수
    private static final int MEDIUM_MEMBER_COUNT = 100;
    private static final int MEDIUM_CONTENT_COUNT = 100;
    private static final int MEDIUM_AD_COUNT = 50;
    private static final int MEDIUM_VIEW_LOG_COUNT = 1000;
    private static final int MEDIUM_AD_VIEW_LOG_COUNT = 500;

    // 대규모 테스트 상수
    private static final int LARGE_MEMBER_COUNT = 1000;
    private static final int LARGE_CONTENT_COUNT = 1000;
    private static final int LARGE_AD_COUNT = 500;
    private static final int LARGE_VIEW_LOG_COUNT = 10000;
    private static final int LARGE_AD_VIEW_LOG_COUNT = 5000;

    // 실제 서비스 규모의 테스트 상수
    private static final int REAL_MEMBER_COUNT = 100_000;        // 10만명의 회원
    private static final int REAL_CONTENT_COUNT = 10_000;        // 1만개의 콘텐츠
    private static final int REAL_AD_COUNT = 5_000;             // 5천개의 광고
    private static final int REAL_VIEW_LOG_COUNT = 10_000_000;  // 1천만개의 시청 로그
    private static final int REAL_AD_VIEW_LOG_COUNT = 5_000_000; // 500만개의 광고 시청 로그

    // 대용량 테스트 상수
    private static final int HUGE_MEMBER_COUNT = 500_000;        // 50만명의 회원
    private static final int HUGE_CONTENT_COUNT = 50_000;        // 5만개의 콘텐츠
    private static final int HUGE_AD_COUNT = 25_000;            // 2.5만개의 광고
    private static final int HUGE_VIEW_LOG_COUNT = 100_000_000; // 1억개의 시청 로그
    private static final int HUGE_AD_VIEW_LOG_COUNT = 50_000_000; // 5천만개의 광고 시청 로그

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private ContentPostRepository contentPostRepository;

    @Autowired
    private ContentStatisticsRepository contentStatisticsRepository;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private DailyWatchedContentRepository dailyWatchedContentRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TestDataGenerator testDataGenerator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MemberContentWatchLogRepository watchLogRepository;

    private final Random random = new Random();

    @BeforeAll
    void setUp() {
        // Redis 초기화는 한 번만
        try {
            redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        } catch (Exception e) {
            log.warn("Redis 초기화 실패: {}", e.getMessage());
        }
    }

    @BeforeEach
    void beforeEach() {
        transactionTemplate.execute(status -> {
            testDataGenerator.clearAllData();
            settlementRepository.deleteAll();
            entityManager.flush();
            entityManager.clear();
            return null;
        });
    }

    @Test
    @Order(1)
    @DisplayName("스트리밍 데이터의 배치 처리 테스트")
    void streamingToBatchTest() throws Exception {
        // 테스트 시작 전에 데이터 상태 로깅
        log.info("초기 Settlement 수: {}", settlementRepository.count());

        LocalDate targetDate = LocalDate.now().minusDays(1);

        // 테스트 데이터 생성
        testDataGenerator.createTestData(
                MEMBER_COUNT,         // 10명의 회원
                CONTENT_COUNT,        // 3개의 콘텐츠
                0,                  // 0개의 광고
                MEMBER_COUNT * CONTENT_COUNT,  // 30개의 시청 로그
                0                   // 0개의 광고 시청 로그
        );

        // 테스트 데이터 생성 후 바로 확인
        log.info("생성된 콘텐츠 수: {}", contentPostRepository.count());

        // Redis 데이터가 DB에 반영될 시간
        Thread.sleep(2000);

        // 일일 시청 데이터 생성
        List<DailyWatchedContent> dailyContents = contentPostRepository.findAll().stream()
                .map(content -> DailyWatchedContent.customBuilder()
                        .contentPostId(content.getId())
                        .watchedDate(targetDate)
                        .build())
                .map(dailyWatchedContentRepository::save)
                .toList();

        // 2. 배치 작업 실행
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("targetDate", targetDate.toString())
                .addLong("startStatisticsId", dailyContents.get(0).getId())
                .addLong("endStatisticsId", dailyContents.get(dailyContents.size() - 1).getId())
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // 3. 검증
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // 통계 데이터 검증
        List<ContentStatistics> stats = contentStatisticsRepository
                .findByStatisticsDateAndPeriod(targetDate, StatisticsPeriod.DAILY);

        // 각 콘텐츠별로 통계가 대로 집계되었는지 확인
        Map<Long, ContentStatistics> statsByContentId = stats.stream()
                .collect(Collectors.groupingBy(
                        stat -> stat.getContentPost().getId(),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> list.get(0)
                        )
                ));

        assertThat(statsByContentId).hasSize(CONTENT_COUNT);
        statsByContentId.values().forEach(stat -> {
            assertThat(stat.getViewCount())
                    .as("각 콘텐츠의 조회수는 멤버 수와 같아야 함")
                    .isEqualTo(MEMBER_COUNT);
            assertThat(stat.getWatchTime())
                    .as("각 콘텐츠의 시청 시간은 (멤버 수 * 250)이어야 함")
                    .isEqualTo(MEMBER_COUNT * 250L);
            assertThat(stat.getAccumulatedViews())
                    .as("누적 조회수는 0보다 커야 함")
                    .isPositive();
        });

        // 정산 데이터 검증
        log.info("최종 Settlement 수: {}", settlementRepository.count());
        List<Settlement> settlements = settlementRepository.findBySettlementDate(targetDate);
        log.info("targetDate에 해당하는 Settlement 수: {}", settlements.size());
        assertThat(settlements).hasSize(CONTENT_COUNT);
        settlements.forEach(settlement -> {
            assertThat(settlement.getContentRevenue()).isPositive();
            assertThat(settlement.getAdRevenue()).isPositive();
            assertThat(settlement.getTotalContentRevenue())
                    .isGreaterThanOrEqualTo(settlement.getContentRevenue());
            assertThat(settlement.getTotalAdRevenue())
                    .isGreaterThanOrEqualTo(settlement.getAdRevenue());
        });
    }

    @Test
    @Order(2)
    @DisplayName("중규모 배치 처리 테스트")
    void mediumScaleBatchTest() throws Exception {
        LocalDate targetDate = LocalDate.now().minusDays(1);
        log.info("중규모 테스트 시작: {}", LocalDateTime.now());

        // 중규모 테스트 데이터 생성
        long startDataGen = System.currentTimeMillis();
        testDataGenerator.createTestData(
                MEDIUM_MEMBER_COUNT,      // 100명의 회원
                MEDIUM_CONTENT_COUNT,     // 100개의 콘텐츠
                MEDIUM_AD_COUNT,          // 50개의 광고
                MEDIUM_VIEW_LOG_COUNT,    // 1000개의 시청 로그
                MEDIUM_AD_VIEW_LOG_COUNT  // 500개의 광고 시청 로그
        );
        long dataGenTime = System.currentTimeMillis() - startDataGen;
        log.info("데이터 생성 소요 시간: {}ms", dataGenTime);
        log.info("생성된 데이터: 회원 {}, 콘텐츠 {}, 광고 {}, 시청로그 {}, 광고시청로그 {}",
                MEDIUM_MEMBER_COUNT, MEDIUM_CONTENT_COUNT, MEDIUM_AD_COUNT,
                MEDIUM_VIEW_LOG_COUNT, MEDIUM_AD_VIEW_LOG_COUNT);

        executeScaleTest(targetDate, MEDIUM_CONTENT_COUNT, Duration.ofMinutes(1));
        
        log.info("중규모 테스트 종료: {}", LocalDateTime.now());
    }

    @Test
    @Order(3)
    @DisplayName("대규모 배치 처리 테스트")
    void largeScaleBatchTest() throws Exception {
        try {
            LocalDate targetDate = LocalDate.now().minusDays(1);
            log.info("데이터 생성 시작: {}", LocalDateTime.now());

            // 1~3. 테스트 데이터 생성 (이전 코드와 동일)
            testDataGenerator.createTestData(
                    LARGE_MEMBER_COUNT,      // 1000명의 회원
                    LARGE_CONTENT_COUNT,     // 1000개의 콘텐츠
                    LARGE_AD_COUNT,          // 500개의 광고
                    LARGE_VIEW_LOG_COUNT,    // 10000개의 시청 로그
                    LARGE_AD_VIEW_LOG_COUNT  // 5000개의 광고 시청 로그
            );

            log.info("테스트 데이터 생성 완료: {}", LocalDateTime.now());

            // 중규모 테스트와 동일한 방식으로 배치 처리 테스트 실행
            executeScaleTest(targetDate, LARGE_CONTENT_COUNT, Duration.ofMinutes(5));

        } catch (Exception e) {
            log.error("테스트 실패: {}", e.getMessage());
            logTableStructures();
            throw e;
        }
    }

    private void executeScaleTest(LocalDate targetDate, int contentCount, Duration maxDuration) throws Exception {
        // 일일 시청 데이터 생성
        List<DailyWatchedContent> dailyContents = contentPostRepository.findAll().stream()
                .limit(contentCount)  // contentCount만큼만 처리
                .map(content -> {
                    // 이미 존재하는 데이터 확인
                    Optional<DailyWatchedContent> existing = dailyWatchedContentRepository
                            .findFirstByContentPostIdAndWatchedDateOrderByIdDesc(content.getId(), targetDate);

                    // 존재하지 않는 경우에만 새로 생성
                    return existing.orElseGet(() ->
                            dailyWatchedContentRepository.save(
                                    DailyWatchedContent.customBuilder()
                                            .contentPostId(content.getId())
                                            .watchedDate(targetDate)
                                            .build()
                            )
                    );
                })
                .toList();

        // 배치 작업 실행 및 성능 측정
        log.info("배치 처리 시작: {}", LocalDateTime.now());
        long startTime = System.currentTimeMillis();

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("targetDate", targetDate.toString())
                .addLong("startStatisticsId", dailyContents.get(0).getId())
                .addLong("endStatisticsId", dailyContents.get(dailyContents.size() - 1).getId())
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        long endTime = System.currentTimeMillis();
        long processingTime = endTime - startTime;
        log.info("배치 처리 완료: {}, 총 소요시간: {}ms", LocalDateTime.now(), processingTime);

        // 검증
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(processingTime)
                .as("전체 처리 시간이 " + maxDuration.toMinutes() + "분 이내여야 함")
                .isLessThan(maxDuration.toMillis());

        // 각 콘텐츠별로 최신 통계 데이터만 가져오기
        List<ContentStatistics> stats = contentStatisticsRepository
                .findByStatisticsDateAndPeriod(targetDate, StatisticsPeriod.DAILY)
                .stream()
                .collect(Collectors.toMap(stat -> stat.getContentPost().getId(), Function.identity(), BinaryOperator.maxBy(Comparator.comparing(ContentStatistics::getId))))
                .values()
                .stream()
                .toList();

        // 시청 로그가 있는 콘텐츠 ID 목록 조회
        Set<Long> contentIdsWithLogs = watchLogRepository.findContentIdsByDate(targetDate);

        // 검증 로직 추가
        log.info("생성된 시청 로그 수: {}", watchLogRepository.count());
        log.info("생성된 통계 데이터 수: {}", stats.size());
        log.info("시청 로그가 있는 콘텐츠 수: {}", contentIdsWithLogs.size());


        // 시청 로그 수가 최소한 일정 수준 이상인지 확인
        assertThat(watchLogRepository.count())
                .as("시청 로그가 최소 " + LARGE_VIEW_LOG_COUNT / 2 + "개 이상 생성되어야 함")
                .isGreaterThan(LARGE_VIEW_LOG_COUNT / 2);
    }

    private void logTableStructures() {
        List<String> tables = List.of("member", "content_post", "member_content_watch_log", "daily_watched_content", "content_statistics", "settlement");

        tables.forEach(table -> {
            log.info("=== {} 테이블 구조 ===", table);
            try {
                jdbcTemplate.queryForList("SHOW COLUMNS FROM " + table)
                        .forEach(column -> log.info("Column: {}", column));
            } catch (Exception e) {
                log.error("테이블 {} 구조 회 실패: {}", table, e.getMessage());
            }
        });
    }
} 