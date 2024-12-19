package com.github.garamflow.streamsettlement.batch.listener;

import com.github.garamflow.streamsettlement.repository.log.DailyWatchedContentQuerydslRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 일일 로그 집계 Step 리스너
 * - Step 실행 전/후 처리를 담당
 * - 대상 날짜의 로그 데이터 처리 관련 작업 수행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyLogAggregationStepListener implements StepExecutionListener {

    private final DailyWatchedContentQuerydslRepository dailyWatchedContentQuerydslRepository;

    private LocalDate targetDate;

    /**
     * Step 실행 전 처리
     * - Job 파라미터에서 대상 날짜 추출
     */
    @Override
    public void beforeStep(StepExecution stepExecution) {
        String targetDateStr = stepExecution.getJobParameters().getString("targetDate");
        this.targetDate = LocalDate.parse(targetDateStr);
    }

    /**
     * Step 실행 후 처리
     * - 현재는 단순히 ExitStatus만 반환
     * - TODO: 로그 집계 관련 후처리 작업 구현 필요
     */
    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        return stepExecution.getExitStatus();
    }
}
