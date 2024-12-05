package com.github.garamflow.streamsettlement.batch.writer.strategy;

import com.github.garamflow.streamsettlement.batch.performance.WriterStrategy;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class JdbcWriter implements WriterStrategy {
    
    private final JdbcTemplate jdbcTemplate;
    
    @Override
    public void write(List<ContentStatistics> statistics) {
        String sql = """
                INSERT INTO content_statistics 
                (content_post_id, statistics_date, period, view_count, watch_time, accumulated_views)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
                
        for (ContentStatistics stat : statistics) {
            jdbcTemplate.update(sql,
                    stat.getContentPost().getId(),
                    stat.getStatisticsDate(),
                    stat.getPeriod().name(),
                    stat.getViewCount(),
                    stat.getWatchTime(),
                    stat.getAccumulatedViews()
            );
        }
    }
} 