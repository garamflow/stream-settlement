package com.github.garamflow.streamsettlement.batch.performance;

import com.github.garamflow.streamsettlement.batch.TestDataGenerator;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
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
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StopWatch;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@SpringBootTest
@SpringBatchTest
@Tag("performance")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LogAggregationConcurrencyTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TestDataGenerator testDataGenerator;

    @BeforeAll
    void setUpOnce() {
        testDataGenerator.createTestData(50, 200, 10, 5000);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 5, 10})
    @DisplayName("동시 실행 부하 테스트")
    void loadTest(int threadCount) throws Exception {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(threadCount);
        taskExecutor.setMaxPoolSize(threadCount);
        taskExecutor.setQueueCapacity(threadCount);
        taskExecutor.setThreadNamePrefix("test-thread-");
        taskExecutor.initialize();

        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // 중간 상태를 모니터링하기 위한 스레드 추가
        Thread monitorThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(1000); // 1초마다 측정
                    HikariDataSource hikariDS = (HikariDataSource) dataSource;
                    log.info("실시간 DB 커넥션 상태 - 활성: {}, 전체: {}, 유휴: {}",
                        hikariDS.getHikariPoolMXBean().getActiveConnections(),
                        hikariDS.getHikariPoolMXBean().getTotalConnections(),
                        hikariDS.getHikariPoolMXBean().getIdleConnections());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        monitorThread.start();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            taskExecutor.execute(() -> {
                try {
                    JobParameters params = new JobParametersBuilder()
                            .addString("targetDate", LocalDate.now().toString())
                            .addLong("timestamp", System.currentTimeMillis())
                            .addString("thread", String.valueOf(index))
                            .toJobParameters();

                    int retryCount = 0;
                    JobExecution execution = null;
                    Exception lastException = null;

                    while (retryCount < 2) {
                        try {
                            Thread.sleep(index * 100);
                            execution = jobLauncherTestUtils.launchJob(params);
                            if (execution.getStatus() == BatchStatus.COMPLETED) {
                                break;
                            }
                            retryCount++;
                            Thread.sleep(500);
                        } catch (Exception e) {
                            lastException = e;
                            log.warn("Retry {} failed for thread {}: {}", 
                                   retryCount + 1, index, e.getMessage());
                            retryCount++;
                            if (retryCount < 2) {
                                Thread.sleep(1000);
                            }
                        }
                    }

                    if (execution != null && execution.getStatus() == BatchStatus.COMPLETED) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                        log.error("Job execution failed for thread {}, status: {}, error: {}", 
                                index,
                                execution != null ? execution.getStatus() : "NULL",
                                lastException != null ? lastException.getMessage() : "Unknown error");
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    log.error("Job 실행 실패 (thread-{}): {}", index, e.getMessage(), e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.MINUTES);
        monitorThread.interrupt(); // 모니터링 중지
        taskExecutor.shutdown();
        
        stopWatch.stop();
        logLoadTestMetrics(threadCount, stopWatch.getTotalTimeSeconds(), 
                          successCount.get(), failCount.get());
    }

    private void logLoadTestMetrics(int threadCount, double totalTime, int successCount, int failCount) {
        HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
        int activeConnections = 0;
        int totalConnections = 0;
        int idleConnections = 0;

        try {
            if (hikariDataSource.getHikariPoolMXBean() != null) {
                activeConnections = hikariDataSource.getHikariPoolMXBean().getActiveConnections();
                totalConnections = hikariDataSource.getHikariPoolMXBean().getTotalConnections();
                idleConnections = hikariDataSource.getHikariPoolMXBean().getIdleConnections();
            }

            log.info("\n");
            log.info("┌──────────────────────────────────────────┐");
            log.info("│            부하 테스트 결과                │");
            log.info("├──────────────────────────────────────────┤");
            log.info("│ 스레드 수: {}", String.format("%-28d", threadCount) + "│");
            log.info("│ 총 처리 시간: {} 초", String.format("%-24.2f", totalTime) + "│");
            log.info("│ 성공 건수: {}", String.format("%-28d", successCount) + "│");
            log.info("│ 실패 건수: {}", String.format("%-28d", failCount) + "│");
            log.info("│ 초당 처리 건수: {} jobs/sec",
                    String.format("%-22.2f", successCount / totalTime) + "│");
            log.info("│ 활성 DB 커넥션: {}",
                    String.format("%-24d", activeConnections) + "│");
            log.info("│ 전체 DB 커넥션: {}",
                    String.format("%-24d", totalConnections) + "│");
            log.info("│ 유휴 DB 커넥션: {}",
                    String.format("%-24d", idleConnections) + "│");
            log.info("└──────────────────────────────────────────┘");
            log.info("\n");

            log.info("HikariCP 설정:");
            log.info("Maximum Pool Size: {}", hikariDataSource.getMaximumPoolSize());
            log.info("Minimum Idle: {}", hikariDataSource.getMinimumIdle());
            log.info("Connection Timeout: {} ms", hikariDataSource.getConnectionTimeout());
        } catch (Exception e) {
            log.error("DB 커넥션 정보 수집 중 오류 발생: {}", e.getMessage());
        }
    }
}
