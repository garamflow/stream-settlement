package com.github.garamflow.streamsettlement.batch.performance;

import com.github.garamflow.streamsettlement.StreamSettlementApplication;
import com.github.garamflow.streamsettlement.batch.TestDataGenerator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.batch.core.*;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBatchTest
@SpringBootTest(classes = StreamSettlementApplication.class)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(properties = {
        "spring.batch.job.enabled=false",
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.jpa.generate-ddl=true",
        "spring.batch.jdbc.initialize-schema=never",
        "spring.sql.init.mode=never",
        "spring.jpa.properties.hibernate.hbm2ddl.auto=update"
})
@EntityScan("com.github.garamflow.streamsettlement")
public class BatchLoadTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private ApplicationContext context;

    private static JdbcTemplate jdbcTemplate;

    @Autowired
    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        BatchLoadTest.jdbcTemplate = jdbcTemplate;
    }

    @BeforeAll
    static void init(@Autowired TestDataGenerator testDataGenerator) {
        testDataGenerator.initialize();
    }

    @BeforeEach
    void setUp() {
        Job job = context.getBean("dailyStatisticsAndSettlementJob", Job.class);
        jobLauncherTestUtils.setJob(job);
    }

    @Test
    @Order(1)
    @DisplayName("10만건 배치 처리 성능 테스트")
    void hundredKTest() throws Exception {
        LocalDate targetDate = LocalDate.of(2024, 12, 16);
        executeLoadTest(HUNDRED_K, "100K", targetDate);
    }

    @Test
    @Order(2)
    @DisplayName("50만건 배치 처리 성능 테스트")
    void fiveHundredKTest() throws Exception {
        LocalDate targetDate = LocalDate.of(2024, 12, 16);
        executeLoadTest(FIVE_HUNDRED_K, "500K", targetDate);
    }

    @Test
    @Order(3)
    @DisplayName("100만건 배치 처리 성능 테스트")
    void oneMillionTest() throws Exception {
        LocalDate targetDate = LocalDate.of(2024, 12, 16);
        executeLoadTest(ONE_MILLION, "1M", targetDate);
    }

    @Test
    @Order(4)
    @DisplayName("1000만건 배치 처리 성능 테스트")
    void tenMillionTest() throws Exception {
        LocalDate targetDate = LocalDate.of(2024, 12, 16);
        executeLoadTest(TEN_MILLION, "10M", targetDate);
    }

    @Test
    @Order(5)
    @DisplayName("1억건 배치 처리 성능 테스트")
    void hundredMillionTest() throws Exception {
        LocalDate targetDate = LocalDate.of(2024, 12, 16);
        executeLoadTest(HUNDRED_MILLION, "100M", targetDate);
    }

    private void executeLoadTest(int dataSize, String testType, LocalDate targetDate) throws Exception {
        long initialMemory = getUsedMemory();
        long startTime = System.currentTimeMillis();

        try {
            log.info("\n===== Starting {} Performance Test =====", testType);
            log.info("Initial memory usage: {} MB", initialMemory / 1024 / 1024);

            // 배치 작업 실행
            JobParameters params = new JobParametersBuilder()
                    .addString("targetDate", targetDate.toString())
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            JobExecution jobExecution = jobLauncherTestUtils.launchJob(params);

            // 성능 메트릭 수집 및 출력
            logPerformanceMetrics(jobExecution, testType, dataSize, startTime);

            // 결과 검증
            assertJobResults(jobExecution, dataSize);

        } finally {
            // 메모리 사용량 변화 기록
            long finalMemory = getUsedMemory();
            log.info("Memory usage change: {} MB", (finalMemory - initialMemory) / 1024 / 1024);
            log.info("===== {} Performance Test Completed =====\n", testType);
        }
    }

    private void logPerformanceMetrics(JobExecution jobExecution, String testType, int dataSize, long startTime) {
        long totalDuration = System.currentTimeMillis() - startTime;

        log.info("\n========== {} Load Test Results ==========", testType);
        log.info("Total Records: {}", dataSize);
        log.info("Total Duration: {} seconds", String.format("%.2f", totalDuration / 1000.0));
        log.info("Average Throughput: {} records/second",
                String.format("%.2f", (double) dataSize / (totalDuration / 1000.0)));

        for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
            long stepDuration = ChronoUnit.MILLIS.between(
                    stepExecution.getStartTime(),
                    stepExecution.getEndTime()
            );

            double stepThroughput = stepExecution.getWriteCount() /
                    Math.max(stepDuration / 1000.0, 0.001);

            log.info("\nStep: {}", stepExecution.getStepName());
            log.info("├─ Read Count: {}", stepExecution.getReadCount());
            log.info("├─ Write Count: {}", stepExecution.getWriteCount());
            log.info("├─ Duration: {} seconds", String.format("%.2f", stepDuration / 1000.0));
            log.info("├─ Throughput: {} records/second", String.format("%.2f", stepThroughput));
            log.info("├─ Commit Count: {}", stepExecution.getCommitCount());
            log.info("├─ Rollback Count: {}", stepExecution.getRollbackCount());
            log.info("└─ Skip Count: {}",
                    stepExecution.getReadSkipCount() +
                            stepExecution.getProcessSkipCount() +
                            stepExecution.getWriteSkipCount());
        }
    }

    private void logResourceUsage() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        log.info("\nSystem Resource Usage:");
        log.info("├─ Used Memory: {} MB", usedMemory / 1024 / 1024);
        log.info("├─ Free Memory: {} MB", freeMemory / 1024 / 1024);
        log.info("└─ Total Memory: {} MB", totalMemory / 1024 / 1024);
    }

    private long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private void assertJobResults(JobExecution jobExecution, int expectedCount) {
        LocalDate date = LocalDate.parse(jobExecution.getJobParameters().getString("targetDate"));

        // 1. 시청 로그 수 확인
        Integer watchLogCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM member_content_watch_log WHERE watched_date = ?",
                Integer.class,
                date
        );

        // 2. daily_watched_content 수 확인
        Integer dailyWatchedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM daily_watched_content WHERE watched_date = ?",
                Integer.class,
                date
        );

        // 3. content_statistics 수 확인
        Integer statisticsCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM content_statistics WHERE statistics_date = ?",
                Integer.class,
                date
        );

        log.info("Watch Log Count: {}", watchLogCount);
        log.info("Daily Watched Count: {}", dailyWatchedCount);
        log.info("Statistics Count: {}", statisticsCount);

        assertThat(watchLogCount).isNotNull().isGreaterThan(0);
        assertThat(dailyWatchedCount).isNotNull().isGreaterThan(0);
        assertThat(statisticsCount).isNotNull().isGreaterThan(0);
//
//        // content_statistics는 콘텐츠별로 집계므로 시청 로그 수와 다를 수 있음
//        assertThat(statisticsCount).isEqualTo(testDataGenerator.getContentCount());
    }

    private static final int HUNDRED_K = 100_000;
    private static final int FIVE_HUNDRED_K = 500_000;
    private static final int ONE_MILLION = 1_000_000;
    private static final int TEN_MILLION = 10_000_000;
    private static final int HUNDRED_MILLION = 100_000_000;
}