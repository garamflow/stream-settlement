package com.github.garamflow.streamsettlement.batch.statistics;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Date;
import java.util.UUID;

/**
 * 통계 집계 작업의 스케줄링을 담당하는 클래스
 * 매일 자정에 통계 집계 작업을 실행합니다.
 */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class StatisticsScheduler {

    private static final Logger log = LoggerFactory.getLogger(StatisticsScheduler.class);
    private final JobLauncher jobLauncher;
    private final Job statisticsAggregationJob;


    /**
     * 모든 기간의 통계 집계 작업을 실행합니다.
     * 매일 자정(한국 시간 기준)에 실행되도록 스케줄링되어 있습니다.
     * 작업 실행 중 발생하는 예외는 로깅됩니다.
     */
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void runAllStatistics() {
        try {
            JobParameters jobParameters = new JobParametersBuilder()
                    .addDate("date", new Date())
                    .addString("id", UUID.randomUUID().toString())
                    .toJobParameters();

            jobLauncher.run(statisticsAggregationJob, jobParameters);
        } catch (Exception e) {
            log.error("Statistics job failed: {}", e.getMessage(), e);
        }
    }
}
