package com.github.garamflow.streamsettlement.batch.performance;

import com.github.garamflow.streamsettlement.batch.TestDataGenerator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StopWatch;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@SpringBootTest
@SpringBatchTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LogAggregationPartitioningTest {

    private record PerformanceMetrics(double totalTimeSeconds, double throughputPerSecond, int deadlockCount,
                                      double avgMemoryUsageMB, double cpuUsagePercent) {
    }

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;


    @Autowired
    private TestDataGenerator testDataGenerator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    void setUp() {
        // 테스트 데이터 생성
        // (멤버 수, 콘텐츠 수, 광고 수, 조회 로그 수)
//        testDataGenerator.createTestData(100, 1000, 20, 100000);
//        testDataGenerator.createTestData(100, 1000, 20, 500000);
        testDataGenerator.createTestData(100, 1000, 20, 1000000);
    }

    @Test
    @DisplayName("파티셔닝 적용 전후 성능 비교")
    void comparePartitioningPerformance() throws Exception {
        // 테스트 데이터 설정
        int[] threadCounts = {5, 10, 20, 50};

        // 파티셔닝 적용 전 테스트
        Map<Integer, PerformanceMetrics> beforePartitioning = new HashMap<>();
        for (int threadCount : threadCounts) {
            log.info("파티셔닝 적용 전 테스트 시작 (스레드 수: {})", threadCount);
            beforePartitioning.put(threadCount, executeLoadTestWithoutPartitioning(threadCount));
            Thread.sleep(5000); // 시스템 안정화를 위한 대기
        }

        // 파티셔닝 적용 후 테스트
        Map<Integer, PerformanceMetrics> afterPartitioning = new HashMap<>();
        for (int threadCount : threadCounts) {
            log.info("파티셔닝 적용 후 테스트 시작 (스레드 수: {})", threadCount);
            afterPartitioning.put(threadCount, executeLoadTestWithPartitioning(threadCount));
            Thread.sleep(5000); // 시스템 안정화를 위한 대기
        }

        // 결과 비교 로깅
        logComparisonResults(beforePartitioning, afterPartitioning);
    }

    private PerformanceMetrics executeLoadTestWithoutPartitioning(int threadCount) throws Exception {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(threadCount);
        taskExecutor.setMaxPoolSize(threadCount);
        taskExecutor.setQueueCapacity(threadCount * 2);
        taskExecutor.setThreadNamePrefix("no-partition-");
        taskExecutor.initialize();

        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger deadlockCount = new AtomicInteger(0);

        // CPU, 메모리 모니터링 시작
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long startMemory = memoryBean.getHeapMemoryUsage().getUsed();
        double startCpu = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();

        // 일반 Job 실행
        for (int i = 0; i < threadCount; i++) {
            taskExecutor.execute(() -> {
                try {
                    JobParameters params = new JobParametersBuilder()
                            .addString("targetDate", LocalDate.now().toString())
                            .addString("chunkSize", "500")
                            .addLong("timestamp", System.currentTimeMillis())
                            .toJobParameters();

                    JobExecution execution = jobLauncherTestUtils.launchJob(params);
                    if (execution.getStatus() == BatchStatus.COMPLETED) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    if (e.getMessage().toLowerCase().contains("deadlock")) {
                        deadlockCount.incrementAndGet();
                    }
                    log.error("Job execution failed", e);
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(30, TimeUnit.MINUTES);
        if (!completed) {
            log.warn("일부 작업이 제한 시간 내에 완료되지 않았습니다.");
        }

        stopWatch.stop();

        // 메트릭스 수집
        long endMemory = memoryBean.getHeapMemoryUsage().getUsed();
        double endCpu = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
        double avgMemoryUsage = (endMemory - startMemory) / (1024.0 * 1024.0); // MB
        double avgCpuUsage = (endCpu + startCpu) / 2.0;

        taskExecutor.shutdown();

        return new PerformanceMetrics(
                stopWatch.getTotalTimeSeconds(),
                successCount.get() / stopWatch.getTotalTimeSeconds(),
                deadlockCount.get(),
                avgMemoryUsage,
                avgCpuUsage
        );
    }

    private PerformanceMetrics executeLoadTestWithPartitioning(int threadCount) throws Exception {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(threadCount);
        taskExecutor.setMaxPoolSize(threadCount);
        taskExecutor.setQueueCapacity(threadCount * 2);
        taskExecutor.setThreadNamePrefix("with-partition-");
        taskExecutor.initialize();

        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger deadlockCount = new AtomicInteger(0);

        // CPU, 메모리 모니터링 시작
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long startMemory = memoryBean.getHeapMemoryUsage().getUsed();
        double startCpu = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();

        // 파티셔닝된 Job 실행
        for (int i = 0; i < threadCount; i++) {
            final int partitionNumber = i;
            taskExecutor.execute(() -> {
                try {
                    JobParameters params = new JobParametersBuilder()
                            .addString("targetDate", LocalDate.now().toString())
                            .addLong("minId", calculateMinId(partitionNumber, threadCount))
                            .addLong("maxId", calculateMaxId(partitionNumber, threadCount))
                            .addString("chunkSize", "500")
                            .addLong("timestamp", System.currentTimeMillis())
                            .toJobParameters();

                    JobExecution execution = jobLauncherTestUtils.launchJob(params);
                    if (execution.getStatus() == BatchStatus.COMPLETED) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    if (e.getMessage().toLowerCase().contains("deadlock")) {
                        deadlockCount.incrementAndGet();
                    }
                    log.error("Job execution failed", e);
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(30, TimeUnit.MINUTES);
        if (!completed) {
            log.warn("일부 작업이 제한 시간 내에 완료되지 않았습니다.");
        }

        stopWatch.stop();

        // 메트릭스 수집
        long endMemory = memoryBean.getHeapMemoryUsage().getUsed();
        double endCpu = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
        double avgMemoryUsage = (endMemory - startMemory) / (1024.0 * 1024.0);
        double avgCpuUsage = (endCpu + startCpu) / 2.0;

        taskExecutor.shutdown();

        return new PerformanceMetrics(
                stopWatch.getTotalTimeSeconds(),
                successCount.get() / stopWatch.getTotalTimeSeconds(),
                deadlockCount.get(),
                avgMemoryUsage,
                avgCpuUsage
        );
    }

    private void logComparisonResults(
            Map<Integer, PerformanceMetrics> before,
            Map<Integer, PerformanceMetrics> after) {
        log.info("\n");
        log.info("┌─────────────────────────────────────────────────────────────────┐");
        log.info("│                     성능 비교 테스트 결과                          │");
        log.info("├──────────────┬────────────────────────┬────────────────────────┤");
        log.info("│              │     파티셔닝 적용 전      │     파티셔닝 적용 후      │");
        log.info("├──────────────┼────────────────────────┼────────────────────────┤");

        before.forEach((threadCount, beforeMetrics) -> {
            PerformanceMetrics afterMetrics = after.get(threadCount);
            log.info("│ 스레드 수: {} │", String.format("%-3d", threadCount));
            log.info("├──────────────┼────────────────────────┼────────────────────────┤");
            log.info("│ 처리 시간     │ {} │ {} │",
                    String.format("%20.2f", beforeMetrics.totalTimeSeconds()),
                    String.format("%20.2f", afterMetrics.totalTimeSeconds()));
            log.info("│ 처리량(tps)   │ {} │ {} │",
                    String.format("%20.2f", beforeMetrics.throughputPerSecond()),
                    String.format("%20.2f", afterMetrics.throughputPerSecond()));
            log.info("│ 데드락 발생   │ {} │ {} │",
                    String.format("%20d", beforeMetrics.deadlockCount()),
                    String.format("%20d", afterMetrics.deadlockCount()));
            log.info("│ 메모리 사용량  │ {}M │ {}M │",
                    String.format("%19.2f", beforeMetrics.avgMemoryUsageMB()),
                    String.format("%19.2f", afterMetrics.avgMemoryUsageMB()));
            log.info("│ CPU 사용률    │ {}% │ {}% │",
                    String.format("%19.2f", beforeMetrics.cpuUsagePercent()),
                    String.format("%19.2f", afterMetrics.cpuUsagePercent()));
            log.info("├──────────────┴────────────────────────┴────────────────────────┤");
        });
        log.info("└─────────────────────────────────────────────────────────────────┘");
    }

    private long calculateMinId(int partitionNumber, int totalPartitions) {
        String sql = """
            SELECT MIN(content_post_id) as min_id, 
                   MAX(content_post_id) as max_id
            FROM daily_member_view_log
            WHERE log_date = ?
            """;
        
        Map<String, Object> minMax = jdbcTemplate.queryForMap(sql, LocalDate.now());
        long min = (long) minMax.get("min_id");
        long max = (long) minMax.get("max_id");
        
        long totalRecords = max - min + 1;
        long targetSize = Math.max(1, totalRecords / totalPartitions);
        
        return min + (partitionNumber * targetSize);
    }

    private long calculateMaxId(int partitionNumber, int totalPartitions) {
        String sql = """
            SELECT MIN(content_post_id) as min_id, 
                   MAX(content_post_id) as max_id
            FROM daily_member_view_log
            WHERE log_date = ?
            """;
        
        Map<String, Object> minMax = jdbcTemplate.queryForMap(sql, LocalDate.now());
        long min = (long) minMax.get("min_id");
        long max = (long) minMax.get("max_id");
        
        long totalRecords = max - min + 1;
        long targetSize = Math.max(1, totalRecords / totalPartitions);
        
        long end = min + ((partitionNumber + 1) * targetSize - 1);
        return Math.min(end, max);
    }
}
