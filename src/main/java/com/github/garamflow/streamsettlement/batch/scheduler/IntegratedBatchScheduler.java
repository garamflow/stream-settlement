package com.github.garamflow.streamsettlement.batch.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 통계 집계 및 정산 처리를 위한 통합 스케줄러
 * 매일 새벽 2시에 전일 데이터에 대한 통계 집계 및 정산 처리를 수행합니다.
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class IntegratedBatchScheduler {

    private final JobLauncher jobLauncher;
    private final Job integratedJob;  // 통합된 Job (통계 + 정산)

    /**
     * 통합 배치 작업 자동 실행
     * 매일 새벽 2시에 다음 작업을 순차적으로 실행:
     * 1. 전일 데이터 통계 집계
     * 2. 집계된 통계 기반 정산 처리
     */
    @Scheduled(cron = "0 0 2 * * ?", zone = "Asia/Seoul")
    public void runIntegratedBatch() {
        try {
            log.info("Starting Integrated Batch Job: {}", LocalDateTime.now());

            JobParameters jobParameters = new JobParametersBuilder()
                    .addString("jobId", UUID.randomUUID().toString())
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            jobLauncher.run(integratedJob, jobParameters);

            log.info("Completed Integrated Batch Job: {}", LocalDateTime.now());
        } catch (Exception e) {
            log.error("Integrated Batch Job Failed: {}", e.getMessage(), e);
        }
    }

    /**
     * 통합 배치 작업 수동 실행
     * 필요시 수동으로 배치 작업을 실행할 수 있는 메서드
     *
     * @throws RuntimeException 배치 작업 실행 중 오류 발생 시
     */
    public void runIntegratedBatchManually() {
        try {
            log.info("Starting Manual Integrated Batch Job: {}", LocalDateTime.now());

            JobParameters jobParameters = new JobParametersBuilder()
                    .addString("jobId", UUID.randomUUID().toString())
                    .addLong("timestamp", System.currentTimeMillis())
                    .addString("type", "manual")
                    .toJobParameters();

            jobLauncher.run(integratedJob, jobParameters);

            log.info("Completed Manual Integrated Batch Job: {}", LocalDateTime.now());
        } catch (Exception e) {
            log.error("Manual Integrated Batch Job Failed: {}", e.getMessage(), e);
            throw new RuntimeException("Integrated batch job failed", e);
        }
    }
}
