package com.github.garamflow.streamsettlement.batch.writer;

import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.repository.statistics.ContentStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class DailyLogWriter implements ItemWriter<List<ContentStatistics>> {
    private final ContentStatisticsRepository contentStatisticsRepository;

    @Override
    public void write(@NonNull Chunk<? extends List<ContentStatistics>> chunk) throws Exception {
        List<ContentStatistics> statistics = chunk.getItems().stream()
                .flatMap(List::stream)
                .toList();
        contentStatisticsRepository.bulkInsertStatistics(statistics);
    }
}