package com.github.garamflow.streamsettlement.batch.listener;

import com.github.garamflow.streamsettlement.repository.log.DailyWatchedContentQueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyLogAggregationStepListener implements StepExecutionListener {

    private final DailyWatchedContentQueryRepository dailyWatchedContentQueryRepository;

    private LocalDate targetDate;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        String targetDateStr = stepExecution.getJobParameters().getString("targetDate");
        this.targetDate = LocalDate.parse(targetDateStr);
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        return stepExecution.getExitStatus();
    }
}
