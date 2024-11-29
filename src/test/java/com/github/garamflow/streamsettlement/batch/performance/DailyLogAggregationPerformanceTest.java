package com.github.garamflow.streamsettlement.batch.performance;

import com.github.garamflow.streamsettlement.batch.TestDataGenerator;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StopWatch;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest
@SpringBatchTest
@Tag("performance")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DailyLogAggregationPerformanceTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private DataSource dataSource;

    private ThreadPoolTaskExecutor taskExecutor;

    @Autowired
    private TestDataGenerator testDataGenerator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    void setUpOnce() {
        testDataGenerator.createTestData(100, 1000, 20, 10000);
    }

    @BeforeEach
    void verifyTestData() {
        // 데이터가 존재하는지 확인
        Long viewLogCount = entityManager.createQuery(
                        "SELECT COUNT(d) FROM DailyMemberViewLog d", Long.class)
                .getSingleResult();

        if (viewLogCount == 0) {
            log.info("테스트 데이터 재생성 시작");
            testDataGenerator.createTestData(100, 1000, 20, 10000);
//            testDataGenerator.createTestData(100, 1000, 20, 100000);
//            testDataGenerator.createTestData(100, 1000, 20, 500000);
//            testDataGenerator.createTestData(100, 1000, 20, 1000000);
            log.info("테스트 데이터 재생성 완료");
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {30, 50, 100, 500, 1000})
    @DisplayName("청크 사이즈별 성능 테스트")
    void chunkSizePerformanceTest(int chunkSize) throws Exception {
        // given
        LocalDate targetDate = LocalDate.now();

        // 데이터 존재 여부 확인
        int count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM daily_member_view_log WHERE log_date = ?",
                Integer.class,
                targetDate
        );
        log.info("Found {} records for date {}", count, targetDate);

        StopWatch stopWatch = new StopWatch();
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

        // when
        stopWatch.start();
        long startMemory = memoryBean.getHeapMemoryUsage().getUsed();

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("targetDate", targetDate.toString())
                .addString("chunkSize", String.valueOf(chunkSize))
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        stopWatch.stop();
        long endMemory = memoryBean.getHeapMemoryUsage().getUsed();

        // then
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // 성능 메트릭 출력
        logPerformanceMetrics(
                chunkSize,
                stopWatch.getTotalTimeSeconds(),
                (endMemory - startMemory) / (1024 * 1024), // MB 단위
                jobExecution.getStepExecutions().iterator().next().getReadCount()
        );
    }

    private void logPerformanceMetrics(int chunkSize, double totalTimeSeconds, long memoryUsedMB, long processedRecords) {
        log.info("\n");
        log.info("┌──────────────────────────────────────────┐");
        log.info("│            성능 테스트 결과                │");
        log.info("├──────────────────────────────────────────┤");
        log.info("│ 청크 사이즈: {}", String.format("%-28d", chunkSize) + "│");
        log.info("│ 총 처리 시간: {} 초", String.format("%-24.2f", totalTimeSeconds) + "│");
        log.info("│ 초당 처리 건수: {} records/sec",
                String.format("%-18.2f", processedRecords / totalTimeSeconds) + "│");
        log.info("│ 메모리 사용량: {} MB", String.format("%-24d", memoryUsedMB) + "│");
        log.info("│ 총 처리 건수: {}", String.format("%-26d", processedRecords) + "│");
        log.info("│ CPU 사용률: {}%",
                String.format("%-27.2f", ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage()) + "│");
        log.info("└──────────────────────────────────────────┘");
        log.info("\n");
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 10, 20, 50})
    @DisplayName("동시 실행 부하 테스트")
    void loadTest(int threadCount) throws Exception {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(threadCount);
        taskExecutor.setMaxPoolSize(threadCount);
        taskExecutor.setQueueCapacity(threadCount * 2);
        taskExecutor.setThreadNamePrefix("test-thread-");
        taskExecutor.initialize();

        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // 재시도 정책 설정
        RetryTemplate retryTemplate = new RetryTemplate();
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(3);
        retryTemplate.setRetryPolicy(retryPolicy);

        // Job 실행
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            taskExecutor.execute(() -> {
                try {
                    retryTemplate.execute(retryContext -> {
                        JobParameters params = new JobParametersBuilder()
                                .addString("targetDate", LocalDate.now().toString())
                                .addString("chunkSize", "500")
                                .addString("thread", String.valueOf(index))
                                .addLong("timestamp", System.currentTimeMillis())
                                .toJobParameters();

                        JobExecution execution = jobLauncherTestUtils.launchJob(params);
                        if (execution.getStatus() == BatchStatus.COMPLETED) {
                            successCount.incrementAndGet();
                        }
                        return execution;
                    });
                } catch (Exception e) {
                    log.error("Job 실행 실패 (thread-{})", index, e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(15, TimeUnit.MINUTES);
        taskExecutor.shutdown();

        stopWatch.stop();
        double totalTime = stopWatch.getTotalTimeSeconds();
        int successCountValue = successCount.get();

        logLoadTestMetrics(threadCount, totalTime, successCountValue);
    }

    private void logLoadTestMetrics(int threadCount, double totalTime, int successCount) {
        log.info("\n");
        log.info("┌──────────────────────────────────────────┐");
        log.info("│            부하 테스트 결과                │");
        log.info("├──────────────────────────────────────────┤");
        log.info("│ 스레드 수: {}", String.format("%-28d", threadCount) + "│");
        log.info("│ 총 처리 시간: {} 초", String.format("%-24.2f", totalTime) + "│");
        log.info("│ 초당 처리 건수: {} jobs/sec",
                String.format("%-22.2f", successCount / totalTime) + "│");
        log.info("│ DB 커넥션 수: {}",
                String.format("%-26d", ((HikariDataSource) dataSource).getHikariPoolMXBean().getActiveConnections()) + "│");
        log.info("│ 스레드 풀 활성도: {}%",
                String.format("%-23.2f", taskExecutor.getActiveCount() * 100.0 / threadCount) + "│");
        log.info("└────────────────────────────────���─────────┘");
        log.info("\n");
    }
}
