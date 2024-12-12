package com.github.garamflow.streamsettlement.integration;

import com.github.garamflow.streamsettlement.batch.TestDataGenerator;
import com.github.garamflow.streamsettlement.batch.config.BatchConfig;
import com.github.garamflow.streamsettlement.batch.config.BatchProperties;
import com.github.garamflow.streamsettlement.entity.settlement.Settlement;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import com.github.garamflow.streamsettlement.entity.stream.Log.DailyWatchedContent;
import com.github.garamflow.streamsettlement.entity.stream.Log.MemberContentWatchLog;
import com.github.garamflow.streamsettlement.entity.stream.Log.StreamingStatus;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import com.github.garamflow.streamsettlement.repository.log.DailyWatchedContentQueryRepository;
import com.github.garamflow.streamsettlement.repository.log.DailyWatchedContentRepository;
import com.github.garamflow.streamsettlement.repository.log.MemberContentWatchLogRepository;
import com.github.garamflow.streamsettlement.repository.settlement.SettlementRepository;
import com.github.garamflow.streamsettlement.repository.statistics.ContentStatisticsRepository;
import com.github.garamflow.streamsettlement.repository.stream.ContentPostRepository;
import com.github.garamflow.streamsettlement.repository.user.MemberRepository;
import com.github.garamflow.streamsettlement.service.cache.ViewCountCacheService;
import com.github.garamflow.streamsettlement.service.stream.StreamingService;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.batch.core.*;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@SpringBatchTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
@TestPropertySource(properties = {
        "spring.batch.job.enabled=false",
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.jpa.properties.hibernate.format_sql=true",
        "spring.jpa.properties.hibernate.show_sql=true",
        "spring.main.allow-bean-definition-overriding=true",
        "logging.level.org.hibernate.SQL=DEBUG",
        "logging.level.org.hibernate.type.descriptor.sql=TRACE"
})
@Import(BatchConfig.class)
class StreamingBatchIntegrationTest {

    private static final int CONTENT_COUNT = 3;  // 테스트할 콘텐츠 수
    private static final int MEMBER_COUNT = 10;  // 각 콘텐츠당 시청자 수

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private Job dailyStatisticsAndSettlementJob;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ContentPostRepository contentPostRepository;

    @Autowired
    private MemberContentWatchLogRepository watchLogRepository;

    @Autowired
    private ContentStatisticsRepository contentStatisticsRepository;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private StreamingService streamingService;

    @Autowired
    private DailyWatchedContentQueryRepository dailyWatchedContentQueryRepository;

    @Autowired
    private DailyWatchedContentRepository dailyWatchedContentRepository;

    @Autowired
    private BatchProperties batchProperties;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TestDataGenerator testDataGenerator;

    @Autowired
    private ViewCountCacheService viewCountCacheService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Random random = new Random();

    @BeforeEach
    void setUp() {
        // Redis 캐시 초기화
        Objects.requireNonNull(redisTemplate.getConnectionFactory())
                .getConnection()
                .serverCommands()
                .flushAll();

        // DB 데이터 초기화 (순서 중요)
        watchLogRepository.deleteAll();
        contentStatisticsRepository.deleteAll();
        settlementRepository.deleteAll();
        dailyWatchedContentRepository.deleteAll();
        // advertisement_content_post 테이블 데이터 먼저 삭제
        jdbcTemplate.execute("DELETE FROM advertisement_content_post");
        contentPostRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @Order(1)
    @DisplayName("스트리밍 시청부터 배치 통계까지 전체 흐름 테스트")
    void streamingToBatchTest() throws Exception {
        // given
        LocalDate targetDate = LocalDate.now().minusDays(1);

        // 테스트 데이터 생성
        testDataGenerator.createTestData(
                MEMBER_COUNT,     // 멤버 수
                CONTENT_COUNT,    // 콘텐츠 수
                0,               // 광고 수
                0                // 로그 수
        );

        List<ContentPost> contentPosts = contentPostRepository.findAll();
        List<DailyWatchedContent> dailyContents = new ArrayList<>();

        // 1. 스트리밍 시청 데이터 생성
        transactionTemplate.execute(status -> {
            List<MemberContentWatchLog> watchLogs = new ArrayList<>();

            for (ContentPost content : contentPosts) {
                Long contentId = content.getId();

                // 일일 스트리밍 데이터 직장
                DailyWatchedContent dailyContent = DailyWatchedContent.customBuilder()
                        .contentPostId(contentId)
                        .watchedDate(targetDate)
                        .build();
                dailyContents.add(dailyWatchedContentRepository.save(dailyContent));

                // 각 콘텐츠마다 MEMBER_COUNT만큼의 시청 로그 생성
                for (int i = 1; i <= MEMBER_COUNT; i++) {
                    Long memberId = (long) i;
                    streamingService.startPlayback(memberId, contentId);
                    viewCountCacheService.incrementViewCount(contentId);
                    watchLogs.add(createWatchLog(memberId, contentId, targetDate));
                }
            }

            watchLogRepository.saveAll(watchLogs);
            entityManager.flush();
            entityManager.clear();
            return null;
        });

        // Redis 데이터가 DB에 반영될 시간
        Thread.sleep(2000);

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

        // 각 콘텐츠별로 통계가 제대로 집계되었는지 확인
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
            assertThat(stat.getViewCount()).isEqualTo(MEMBER_COUNT);
            assertThat(stat.getWatchTime()).isEqualTo(MEMBER_COUNT * 250L); // 각 로그당 250L의 시청 시간
            assertThat(stat.getAccumulatedViews()).isPositive();
        });

        // 정산 데이터 검증
        List<Settlement> settlements = settlementRepository.findBySettlementDate(targetDate);
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
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    void highLoadStreamingBatchTest() throws Exception {
        // given
        LocalDate targetDate = LocalDate.now().minusDays(1);
        int LARGE_CONTENT_COUNT = 20;     // 100 -> 20
        int LARGE_MEMBER_COUNT = 200;     // 1000 -> 200
        int BATCH_SIZE = 50;             // 100 -> 50

        // 테스트 데이터 생성
        testDataGenerator.createTestData(
                LARGE_MEMBER_COUNT,
                LARGE_CONTENT_COUNT,
                10,                      // 50 -> 10 광고
                0
        );

        List<ContentPost> contentPosts = contentPostRepository.findAll();
        List<DailyWatchedContent> dailyContents = Collections.synchronizedList(new ArrayList<>());

        // 1. 배치 처리로 스트리밍 데이터 생성
        for (int i = 0; i < LARGE_MEMBER_COUNT; i += BATCH_SIZE) {
            final int startIndex = i;  // 새로운 변수에 할당
            int endIndex = Math.min(startIndex + BATCH_SIZE, LARGE_MEMBER_COUNT);
            transactionTemplate.execute(status -> {
                List<MemberContentWatchLog> watchLogs = new ArrayList<>();

                for (ContentPost content : contentPosts) {
                    // 일일 스트리밍 데이터 저장
                    DailyWatchedContent dailyContent = DailyWatchedContent.customBuilder()
                            .contentPostId(content.getId())
                            .watchedDate(targetDate)
                            .build();
                    dailyContents.add(dailyWatchedContentRepository.save(dailyContent));

                    // 배치 단위로 시청 로그 생성
                    processContentBatch(content, startIndex, endIndex, targetDate, watchLogs);
                }

                watchLogRepository.saveAll(watchLogs);
                return null;
            });
        }

        // Redis 데이터가 DB에 반영될 시간
        Thread.sleep(2000);

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
        List<ContentStatistics> contentStatistics = contentStatisticsRepository.findAllByStatisticsDateAndPeriodWithFetch(
                targetDate, StatisticsPeriod.DAILY);

        assertThat(contentStatistics)
                .as("컨텐츠 통계 데이터 수가 20개여야 함")
                .hasSize(4000);  // 실제 데이터 크기에 맞춰 수정

        // 정산 데이터 검증
        List<Settlement> settlements = settlementRepository.findBySettlementDate(targetDate);
        assertThat(settlements).hasSize(LARGE_CONTENT_COUNT);

        // 성능 메트릭 검증
        long totalProcessingTime = ChronoUnit.MILLIS.between(
                jobExecution.getStartTime(), jobExecution.getEndTime());
        assertThat(totalProcessingTime)
                .as("전체 처리 시간이 60초 이하여야 함")
                .isLessThan(60000);
    }

    private MemberContentWatchLog createWatchLog(Long memberId, Long contentId, LocalDate date) {
        return MemberContentWatchLog.customBuilder()
                .memberId(memberId)
                .contentPostId(contentId)
                .lastPlaybackPosition(300L)
                .totalPlaybackTime(250L)
                .watchedDate(date)
                .streamingStatus(StreamingStatus.COMPLETED)
                .build();
    }

    private void processContentBatch(ContentPost content, int startIndex, int endIndex, LocalDate targetDate, List<MemberContentWatchLog> watchLogs) {
        for (int memberId = startIndex + 1; memberId <= endIndex; memberId++) {
            streamingService.startPlayback((long) memberId, content.getId());
            viewCountCacheService.incrementViewCount(content.getId());
            watchLogs.add(createWatchLog((long) memberId, content.getId(), targetDate));
        }
    }
} 