package com.github.garamflow.streamsettlement.batch.writer.strategy;

import com.github.garamflow.streamsettlement.batch.performance.WriterStrategy;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BulkJdbcWriter implements WriterStrategy {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void write(List<ContentStatistics> statistics) {
        String sql = """
                INSERT INTO content_statistics 
                (content_post_id, statistics_date, period, view_count, watch_time, accumulated_views)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        jdbcTemplate.batchUpdate(sql,
                statistics,
                statistics.size(),
                (PreparedStatement ps, ContentStatistics stat) -> {
                    ps.setLong(1, stat.getContentPost().getId());
                    ps.setObject(2, stat.getStatisticsDate());
                    ps.setString(3, stat.getPeriod().name());
                    ps.setLong(4, stat.getViewCount());
                    ps.setLong(5, stat.getWatchTime());
                    ps.setLong(6, stat.getAccumulatedViews());
                });
    }
} 