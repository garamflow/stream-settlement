package com.github.garamflow.streamsettlement.batch.writer;

import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyLogWriter implements ItemWriter<List<ContentStatistics>> {

    private final JdbcTemplate jdbcTemplate;
    private static final int BATCH_SIZE = 500;

    private static final String INSERT_SQL = """
            INSERT IGNORE INTO content_statistics
            (content_post_id, statistics_date, period, view_count, watch_time, accumulated_views)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void write(Chunk<? extends List<ContentStatistics>> chunk) {
        if (chunk.isEmpty()) {
            return;
        }

        List<ContentStatistics> allStats = chunk.getItems().stream()
                .flatMap(List::stream)
                .toList();

        try {
            jdbcTemplate.batchUpdate(INSERT_SQL,
                    allStats,
                    allStats.size(),
                    (PreparedStatement ps, ContentStatistics stat) -> {
                        ps.setLong(1, stat.getContentPost().getId());
                        ps.setObject(2, stat.getStatisticsDate());
                        ps.setString(3, stat.getPeriod().name());
                        ps.setLong(4, stat.getViewCount());
                        ps.setLong(5, stat.getWatchTime());
                        ps.setLong(6, stat.getAccumulatedViews());
                    });
        } catch (Exception e) {
            log.error("Error during bulk insert", e);
            throw new RuntimeException("Bulk insert failed", e);
        }
    }
}