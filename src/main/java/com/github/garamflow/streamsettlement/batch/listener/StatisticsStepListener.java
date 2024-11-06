package com.github.garamflow.streamsettlement.batch.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class StatisticsStepListener implements StepExecutionListener {

    @Override
    public void beforeStep(@NonNull StepExecution stepExecution) {
        log.info("Starting statistics aggregation step");
        stepExecution.getExecutionContext().put("startTime", System.currentTimeMillis());
    }

    @Override
    @NonNull
    public ExitStatus afterStep(@NonNull StepExecution stepExecution) {
        try {
            ExecutionContext stepContext = stepExecution.getExecutionContext();
            ExecutionContext jobContext = stepExecution.getJobExecution().getExecutionContext();

            // 처리 시간 기록
            long startTime = stepContext.getLong("startTime", 0L);
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            // 처리된 통계 데이터 수 기록
            long processedCount = stepExecution.getWriteCount();
            jobContext.putLong("totalStatisticsCount", processedCount);

            // 정산 처리를 위한 날짜 정보 저장
            LocalDate statisticsDate = LocalDate.now().minusDays(1);
            jobContext.put("settlementDate", statisticsDate);

            // 통계 데이터 검증
            if (processedCount == 0) {
                log.warn("No statistics were processed. Settlement step may be skipped.");
                return ExitStatus.COMPLETED.addExitDescription("No statistics processed");
            }

            log.info("Completed statistics aggregation. Processed {} records in {}ms",
                    processedCount, duration);

            // 정산 처리를 위한 추가 정보 설정
            jobContext.put("statisticsProcessed", true);

            return ExitStatus.COMPLETED;

        } catch (Exception e) {
            log.error("Error in statistics step listener", e);
            return ExitStatus.FAILED.addExitDescription(e.getMessage());
        }
    }
}
