package com.github.garamflow.streamsettlement.batch.writer;

import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.repository.statistics.ContentStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyLogWriter implements ItemWriter<List<ContentStatistics>> {
    private final ContentStatisticsRepository contentStatisticsRepository;

    @Override
    public void write(Chunk<? extends List<ContentStatistics>> chunk) {
        List<ContentStatistics> allStats = chunk.getItems().stream()
                .flatMap(List::stream)
                .sorted(Comparator.comparing(stat -> stat.getContentPost().getId()))
                .toList();
        contentStatisticsRepository.bulkInsertStatistics(allStats);
    }
}