package com.github.garamflow.streamsettlement.batch.test;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/batch")
@RequiredArgsConstructor
public class BatchController {

    private final JobLauncher jobLauncher;
    private final Job dailyStatisticsAndSettlementJob;

    /**
     * 일일 통계 및 정산 배치 작업 실행
     *
     * @param targetDate 처리 대상 날짜 (yyyy-MM-dd)
     * @param dataSize 테스트 데이터 크기 (100K, 500K, 1M, 10M, 100M)
     * @return 작업 실행 ID
     */
    @PostMapping("/daily-statistics-settlement")
    public ResponseEntity<Map<String, Long>> runDailyBatchJob(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate targetDate,
            @RequestParam(required = false) Integer dataSize) {
        try {
            JobParameters jobParameters = new JobParametersBuilder()
                    .addString("targetDate", targetDate.toString())
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            var execution = jobLauncher.run(dailyStatisticsAndSettlementJob, jobParameters);

            Map<String, Long> result = new HashMap<>();
            result.put("jobExecutionId", execution.getId());
            result.put("status", (long) execution.getStatus().ordinal());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("배치 작업 실행 중 오류 발생", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 배치 작업 상태 조회
     *
     * @param jobExecutionId 작업 실행 ID
     */
    @GetMapping("/status/{jobExecutionId}")
    public ResponseEntity<Map<String, Object>> getJobStatus(@PathVariable Long jobExecutionId) {
        // TODO: JobExplorer를 사용하여 작업 상태 조회 구현
        return ResponseEntity.ok(Map.of("status", "NOT_IMPLEMENTED"));
    }
}
