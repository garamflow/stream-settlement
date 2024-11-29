package com.github.garamflow.streamsettlement.batch.writer;

import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class DailyLogWriter implements ItemWriter<ContentStatistics> {

    private final JdbcTemplate jdbcTemplate;
    private static final Object GLOBAL_LOCK = new Object();

    private static final String INSERT_OR_UPDATE_SQL = """
            INSERT INTO content_statistics
            (content_post_id, statistics_date, period, view_count, watch_time, accumulated_views)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
            view_count = view_count + VALUES(view_count),
            watch_time = watch_time + VALUES(watch_time),
            accumulated_views = VALUES(accumulated_views)
            """;

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void write(Chunk<? extends ContentStatistics> chunk) {
        List<ContentStatistics> sortedItems = chunk.getItems().stream()
                .sorted(Comparator.comparing(stats -> stats.getContentPost().getId()))
                .collect(Collectors.toList());

        Map<Long, List<ContentStatistics>> groupedStats = sortedItems.stream()
                .collect(Collectors.groupingBy(stats -> stats.getContentPost().getId()));

        synchronized (GLOBAL_LOCK) {
            for (Map.Entry<Long, List<ContentStatistics>> entry : groupedStats.entrySet()) {
                List<ContentStatistics> statsGroup = entry.getValue();
                ContentPost contentPost = statsGroup.get(0).getContentPost();
                long totalWatchTime = 0;

                List<Object[]> batchArgs = new ArrayList<>();
                for (ContentStatistics stats : statsGroup) {
                    totalWatchTime += stats.getWatchTime();
                    batchArgs.add(new Object[]{
                            contentPost.getId(),
                            stats.getStatisticsDate(),
                            stats.getPeriod().name(),
                            stats.getViewCount(),
                            stats.getWatchTime(),
                            contentPost.getTotalViews()
                    });
                }

                contentPost.addWatchTime(totalWatchTime);
                try {
                    jdbcTemplate.batchUpdate(INSERT_OR_UPDATE_SQL, batchArgs);
                    log.debug("Processed {} statistics entries for content {}", 
                            statsGroup.size(), contentPost.getId());
                } catch (Exception e) {
                    log.error("Error while writing statistics for content {}", 
                            contentPost.getId(), e);
                    throw e;
                }
            }
        }
    }
}