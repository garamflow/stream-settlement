package com.github.garamflow.streamsettlement.batch.performance;

import com.github.garamflow.streamsettlement.batch.TestDataGenerator;
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
import org.springframework.util.StopWatch;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest
@SpringBatchTest
@Tag("performance")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LogAggregationChunkSizeTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private TestDataGenerator testDataGenerator;

    @BeforeAll
    void setUpOnce() {
        testDataGenerator.createTestData(100, 1000, 20, 10000);
//            testDataGenerator.createTestData(100, 1000, 20, 100000);
//            testDataGenerator.createTestData(100, 1000, 20, 500000);
//            testDataGenerator.createTestData(100, 1000, 20, 1000000);
    }

    @ParameterizedTest
    @ValueSource(ints = {30, 50, 100, 500, 1000})
    @DisplayName("청크 사이즈별 성능 테스트")
    void chunkSizePerformanceTest(int chunkSize) throws Exception {
        // given
        LocalDate targetDate = LocalDate.now();
        StopWatch stopWatch = new StopWatch();
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

        // when
        stopWatch.start();
        long startMemory = memoryBean.getHeapMemoryUsage().getUsed();

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("targetDate", targetDate.toString())
                .addString("chunkSize", String.valueOf(chunkSize))
                .addString("gridSize", "5")
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        stopWatch.stop();
        long endMemory = memoryBean.getHeapMemoryUsage().getUsed();

        // then
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        logPerformanceMetrics(
                chunkSize,
                stopWatch.getTotalTimeSeconds(),
                (endMemory - startMemory) / (1024 * 1024),
                jobExecution.getStepExecutions().iterator().next().getReadCount()
        );
    }

    private void logPerformanceMetrics(int chunkSize, double totalTimeSeconds,
                                       long memoryUsedMB, long processedRecords) {
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
}
