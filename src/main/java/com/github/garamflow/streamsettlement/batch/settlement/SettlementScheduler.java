package com.github.garamflow.streamsettlement.batch.settlement;

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

/**
 * [최초 실행 진입점]
 * 정산 배치 작업의 실행을 스케줄링하는 클래스입니다.
 * SettlementBatchConfig에 정의된 배치 작업을 실행시키는 스케줄러 역할을 합니다.
 *
 * <p>실행 방식:</p>
 * <ul>
 *   <li>자동 실행: 매일 새벽 2시</li>
 *   <li>수동 실행: runSettlementManually() 메서드 호출</li>
 * </ul>
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class SettlementScheduler {

    private final JobLauncher jobLauncher;
    private final Job dailySettlementJob;

    /**
     * [실행순서 1 - 자동실행]
     * 매일 새벽 2시에 정산 배치 작업을 자동으로 실행합니다.
     * 다음 순서로 처리됩니다:
     * 1. 시작 로그 기록
     * 2. Job 파라미터 생성 (현재 시간 기반)
     * 3. SettlementBatchConfig의 dailySettlementJob 실행
     * 4. 종료 로그 기록
     * 5. 오류 발생 시 에러 로그 기록
     *
     * <p>실행 시간: 매일 02:00 (cron = "0 0 2 * * ?")</p>
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void runDailySettlement() {
        try {
            log.info("Daily Settlement Batch Start: {}", LocalDateTime.now());

            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .toJobParameters();

            jobLauncher.run(dailySettlementJob, jobParameters);

            log.info("Daily Settlement Batch End: {}", LocalDateTime.now());
        } catch (Exception e) {
            log.error("Daily Settlement Batch Failed: {}", e.getMessage(), e);
        }
    }

    /**
     * [실행순서 1 - 수동실행]
     * 정산 배치 작업을 수동으로 실행하는 메서드입니다.
     * runDailySettlement()와 동일한 배치 작업을 실행하지만, 다음과 같은 차이가 있습니다:
     * 1. 시작 로그에 'Manual' 표시
     * 2. Job 파라미터에 type=manual 추가
     * 3. 오류 발생 시 RuntimeException 발생
     *
     * <p>실행 과정:</p>
     * 1. 시작 로그 기록
     * 2. Job 파라미터 생성 (현재 시간 + 수동실행 구분자)
     * 3. SettlementBatchConfig의 dailySettlementJob 실행
     * 4. 종료 로그 기록
     * 5. 오류 발생 시 예외 발생
     *
     * @throws RuntimeException 배치 작업 실행 중 오류 발생 시 (자동실행과 달리 예외를 던짐)
     */
    public void runSettlementManually() {
        try {
            log.info("Manual Settlement Batch Start: {}", LocalDateTime.now());

            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .addString("type", "manual")
                    .toJobParameters();

            jobLauncher.run(dailySettlementJob, jobParameters);

            log.info("Manual Settlement Batch End: {}", LocalDateTime.now());
        } catch (Exception e) {
            log.error("Manual Settlement Batch Failed: {}", e.getMessage(), e);
            throw new RuntimeException("Settlement batch job failed", e);
        }
    }
}
