package com.github.garamflow.streamsettlement.integration;

import com.github.garamflow.streamsettlement.batch.TestDataGenerator;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import com.github.garamflow.streamsettlement.entity.stream.Log.DailyWatchedContent;
import com.github.garamflow.streamsettlement.entity.stream.Log.MemberContentWatchLog;
import com.github.garamflow.streamsettlement.entity.stream.Log.StreamingStatus;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import com.github.garamflow.streamsettlement.repository.log.DailyWatchedContentRepository;
import com.github.garamflow.streamsettlement.repository.log.MemberContentWatchLogRepository;
import com.github.garamflow.streamsettlement.repository.statistics.ContentStatisticsRepository;
import com.github.garamflow.streamsettlement.repository.stream.ContentPostRepository;
import com.github.garamflow.streamsettlement.service.cache.ViewCountCacheService;
import com.github.garamflow.streamsettlement.service.stream.StreamingService;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
class StreamingBatchIntegrationTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;


    @Autowired
    private TestDataGenerator testDataGenerator;

    @Autowired
    private StreamingService streamingService;

    @Autowired
    private ContentStatisticsRepository contentStatisticsRepository;

    @Autowired
    private MemberContentWatchLogRepository watchLogRepository;

    @Autowired
    private ViewCountCacheService viewCountCacheService;

    @Autowired
    private ContentPostRepository contentPostRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DailyWatchedContentRepository dailyWatchedContentRepository;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        try {
            // Redis 캐시 초기화
            Objects.requireNonNull(redisTemplate.getConnectionFactory())
                    .getConnection()
                    .serverCommands()
                    .flushAll();
        } catch (Exception e) {
            log.error("setUp 중 오류 발생", e);
            throw e;
        }
    }

    @Test
    @Order(1)
    @DisplayName("스트리밍 시청부터 배치 통계까지 전체 흐름 테스트")
    void streamingToBatchTest() throws Exception {
        // given
        LocalDate targetDate = LocalDate.now().minusDays(1);
        List<ContentPost> contentPosts = contentPostRepository.findAll();

        // 1. 스트리밍 시청 데이터 생성
        transactionTemplate.execute(status -> {
            List<MemberContentWatchLog> watchLogs = new ArrayList<>();

            for (ContentPost content : contentPosts) {
                Long contentId = content.getId();

                // 일일 스트리밍 데이터 직접 저장
                DailyWatchedContent dailyContent = DailyWatchedContent.customBuilder()
                        .contentPostId(contentId)
                        .watchedDate(targetDate)
                        .build();
                dailyWatchedContentRepository.save(dailyContent);

                for (int i = 1; i <= 10; i++) {
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
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // 3. 결과 검증
        List<ContentStatistics> stats = contentStatisticsRepository
                .findByStatisticsDateAndPeriod(targetDate, StatisticsPeriod.DAILY);

        assertThat(stats).isNotEmpty();
        stats.forEach(stat -> {
            assertThat(stat.getViewCount()).isEqualTo(2000L);
            assertThat(stat.getWatchTime()).isPositive();
        });
    }

    @Test
    @Order(2)
    @DisplayName("대용량 데이터 배치 처리 테스트 (10만 건)")
    void largeBatchTest() throws Exception {
        // given
        LocalDate targetDate = LocalDate.now().minusDays(1);
        int CONTENT_COUNT = 100;    // 컨텐츠 100개
        int USERS_PER_CONTENT = 1000; // 컨텐츠당 사용자 1000명

        // TestDataGenerator를 사용하여 테스트 데이터 생성
        testDataGenerator.createTestData(
                USERS_PER_CONTENT,  // 멤버 수
                CONTENT_COUNT,      // 콘텐츠 수
                0,                  // 광고 수
                0                   // 로그 수
        );

        transactionTemplate.execute(status -> {
            List<MemberContentWatchLog> watchLogs = new ArrayList<>();

            for (int i = 1; i <= CONTENT_COUNT; i++) {  // 0부터가 아닌 1부터 시작
                Long contentId = (long) i;

                // 일일 스트리밍 데이터 저장
                DailyWatchedContent dailyContent = DailyWatchedContent.customBuilder()
                        .contentPostId(contentId)
                        .watchedDate(targetDate)
                        .build();
                dailyWatchedContentRepository.save(dailyContent);

                // 각 컨텐츠당 1000명의 시청 로그 생성
                for (int userId = 1; userId <= USERS_PER_CONTENT; userId++) {
                    watchLogs.add(createWatchLog((long) userId, contentId, targetDate));
                }

                // 1000건씩 벌크 저장
                if (watchLogs.size() >= 1000) {
                    watchLogRepository.saveAll(watchLogs);
                    watchLogs.clear();
                    entityManager.flush();
                    entityManager.clear();
                }
            }

            // 남은 로그 저장
            if (!watchLogs.isEmpty()) {
                watchLogRepository.saveAll(watchLogs);
            }
            return null;
        });

        // 배치 작업 실행
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("targetDate", targetDate.toString())
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // 결과 검증
        List<ContentStatistics> stats = contentStatisticsRepository
                .findByStatisticsDateAndPeriod(targetDate, StatisticsPeriod.DAILY);

        assertThat(stats).hasSize(CONTENT_COUNT);
        stats.forEach(stat -> {
            assertThat(stat.getViewCount()).isEqualTo(USERS_PER_CONTENT);
            assertThat(stat.getWatchTime()).isPositive();
        });
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
} 