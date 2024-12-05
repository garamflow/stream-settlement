package com.github.garamflow.streamsettlement.batch.performance;

import com.github.garamflow.streamsettlement.batch.TestDataGenerator;
import com.github.garamflow.streamsettlement.batch.performance.util.PerformanceVisualizer;
import lombok.AllArgsConstructor;
import lombok.Getter;
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
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.StopWatch;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@SpringBootTest
@SpringBatchTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
class BatchPerformanceTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private TestDataGenerator testDataGenerator;

    @BeforeAll
    void setUp() {
        testDataGenerator.createTestData(100, 1000, 20, 10000);
    }

    @Test
    @DisplayName("파티션 크기별 성능 비교 테스트")
    void partitionSizePerformanceTest() throws Exception {
        List<Integer> partitionSizes = Arrays.asList(2, 4, 8, 16);
        List<Double> throughputResults = new ArrayList<>();
        List<Double> memoryUsages = new ArrayList<>();

        for (int partitionSize : partitionSizes) {
            PerformanceResult result = executePartitionTest(partitionSize);
            throughputResults.add(result.getThroughput());
            memoryUsages.add(result.getMemoryUsage());

            // 시스템 안정화를 위한 대기
            Thread.sleep(5000);
        }

        // 처리량 차트 생성
        PerformanceVisualizer.createPerformanceChart(
                "파티션 크기별 처리량",
                partitionSizes,
                Collections.singletonList(throughputResults),
                "파티션 크기",
                "처리량 (records/sec)",
                "performance-results/partition-throughput.png"
        );

        // 메모리 사용량 차트 생성
        PerformanceVisualizer.createPerformanceChart(
                "파티션 크기별 메모리 사용량",
                partitionSizes,
                Collections.singletonList(memoryUsages),
                "파티션 크기",
                "메모리 사용량 (MB)",
                "performance-results/partition-memory.png"
        );
    }

    @Test
    @DisplayName("동시성 부하 테스트")
    void concurrencyLoadTest() throws Exception {
        List<Integer> threadCounts = Arrays.asList(2, 4, 8, 16);
        List<Double> throughputResults = new ArrayList<>();
        List<Integer> deadlockCounts = new ArrayList<>();

        for (int threadCount : threadCounts) {
            ConcurrencyResult result = executeConcurrencyTest(threadCount);
            throughputResults.add(result.getThroughput());
            deadlockCounts.add(result.getDeadlockCount());

            // 시스템 안정화를 위한 대기
            Thread.sleep(5000);
        }

        // 처리량 차트 생성
        PerformanceVisualizer.createPerformanceChart(
                "스레드 수에 따른 처리량",
                threadCounts,
                Collections.singletonList(throughputResults),
                "스레드 수",
                "처리량 (jobs/sec)",
                "performance-results/concurrency-throughput.png"
        );

        // 데드락 발생 차트 생성
        PerformanceVisualizer.createPerformanceChart(
                "스레드 수에 따른 데드락 발생",
                threadCounts,
                Collections.singletonList(deadlockCounts.stream()
                        .map(Double::valueOf)
                        .collect(Collectors.toList())),
                "스레드 수",
                "데드락 발생 횟수",
                "performance-results/concurrency-deadlocks.png"
        );
    }

    @Getter
    @AllArgsConstructor
    private static class PerformanceResult {
        private double throughput;
        private double memoryUsage;
    }

    @Getter
    @AllArgsConstructor
    private static class ConcurrencyResult {
        private double throughput;
        private int deadlockCount;
    }

    private PerformanceResult executePartitionTest(int partitionSize) throws Exception {
        StopWatch stopWatch = new StopWatch();
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

        stopWatch.start();
        long startMemory = memoryBean.getHeapMemoryUsage().getUsed();

        JobParameters params = new JobParametersBuilder()
                .addString("targetDate", LocalDate.now().toString())
                .addString("partitionSize", String.valueOf(partitionSize))
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        stopWatch.stop();
        long endMemory = memoryBean.getHeapMemoryUsage().getUsed();

        double throughput = execution.getStepExecutions().iterator().next().getReadCount() /
                stopWatch.getTotalTimeSeconds();
        double memoryUsage = (endMemory - startMemory) / (1024.0 * 1024.0); // MB

        logPartitionTestMetrics(partitionSize, stopWatch.getTotalTimeSeconds(),
                throughput, memoryUsage);

        return new PerformanceResult(throughput, memoryUsage);
    }

    private ConcurrencyResult executeConcurrencyTest(int threadCount) throws Exception {
        StopWatch stopWatch = new StopWatch();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger deadlockCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threadCount);

        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(threadCount);
        taskExecutor.setMaxPoolSize(threadCount);
        taskExecutor.setQueueCapacity(threadCount);
        taskExecutor.initialize();

        stopWatch.start();

        for (int i = 0; i < threadCount; i++) {
            taskExecutor.execute(() -> {
                try {
                    JobParameters params = new JobParametersBuilder()
                            .addString("targetDate", LocalDate.now().toString())
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
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.MINUTES);
        taskExecutor.shutdown();
        stopWatch.stop();

        double throughput = successCount.get() / stopWatch.getTotalTimeSeconds();

        logConcurrencyTestMetrics(threadCount, stopWatch.getTotalTimeSeconds(),
                throughput, successCount.get(), deadlockCount.get());

        return new ConcurrencyResult(throughput, deadlockCount.get());
    }

    private void logPartitionTestMetrics(int partitionSize, double totalTimeSeconds,
                                         double throughput, double memoryUsage) {
        log.info("\n");
        log.info("┌──────────────────────────────────────────┐");
        log.info("│         파티션 성능 테스트 결과             │");
        log.info("├──────────────────────────────────────────┤");
        log.info("│ 파티션 크기: {}", String.format("%-26d", partitionSize) + "│");
        log.info("│ 총 처리 시간: {} 초", String.format("%-22.2f", totalTimeSeconds) + "│");
        log.info("│ 초당 처리 건수: {} rec/sec",
                String.format("%-20.2f", throughput) + "│");
        log.info("│ 메모리 사용량: {} MB", String.format("%-22.2f", memoryUsage) + "│");
        log.info("└──────────────────────────────────────────┘\n");
    }

    private void logConcurrencyTestMetrics(int threadCount, double totalTimeSeconds,
                                           double throughput, int successCount, int deadlockCount) {
        log.info("\n");
        log.info("┌──────────────────────────────────────────┐");
        log.info("│            동시성 테스트 결과              │");
        log.info("├──────────────────────────────────────────┤");
        log.info("│ 스레드 수: {}", String.format("%-28d", threadCount) + "│");
        log.info("│ 총 처리 시간: {} 초", String.format("%-22.2f", totalTimeSeconds) + "│");
        log.info("│ 초당 처리 건수: {} jobs/sec",
                String.format("%-20.2f", throughput) + "│");
        log.info("│ 성공 건수: {}", String.format("%-28d", successCount) + "│");
        log.info("│ 데드락 발생: {}", String.format("%-27d", deadlockCount) + "│");
        log.info("└──────────────────────────────────────────┘\n");
    }
}