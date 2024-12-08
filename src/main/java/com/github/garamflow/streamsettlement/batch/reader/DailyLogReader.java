package com.github.garamflow.streamsettlement.batch.reader;

import com.github.garamflow.streamsettlement.batch.config.BatchProperties;
import com.github.garamflow.streamsettlement.entity.stream.Log.DailyMemberViewLog;
import com.github.garamflow.streamsettlement.repository.log.DailyMemberViewLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;


@Slf4j
@Component
@RequiredArgsConstructor
public class DailyLogReader {

    private final DailyMemberViewLogRepository dailyMemberViewLogRepository;
    private final BatchProperties batchProperties;

    @Bean
    @StepScope
    public JdbcPagingItemReader<DailyMemberViewLog> reader(
            @Value("#{jobParameters['targetDate']}") String targetDateStr,
            @Value("#{stepExecutionContext['minId']}") Long minId,
            @Value("#{stepExecutionContext['maxId']}") Long maxId) {

        if (minId == null || maxId == null) {
            throw new IllegalStateException("StepExecutionContext missing 'minId' or 'maxId'.");
        }

        LocalDate targetDate = validateAndParseDate(targetDateStr);
        log.info("Reader initialized for targetDate: {}, minId: {}, maxId: {}", targetDate, minId, maxId);

        return dailyMemberViewLogRepository.createPagingReader(targetDate, minId, maxId, batchProperties.getChunkSize());
    }

    private LocalDate validateAndParseDate(String targetDateStr) {
        if (targetDateStr == null || targetDateStr.isBlank()) {
            throw new IllegalArgumentException("Job parameter 'targetDate' is required but not provided.");
        }

        try {
            return LocalDate.parse(targetDateStr);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid 'targetDate' format. Expected format is yyyy-MM-dd.", e);
        }
    }
}