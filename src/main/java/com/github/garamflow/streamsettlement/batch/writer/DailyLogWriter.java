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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class DailyLogWriter implements ItemWriter<ContentStatistics> {

    private final JdbcTemplate jdbcTemplate;

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
    @Transactional
    public void write(Chunk<? extends ContentStatistics> chunk) {
        List<Object[]> batchArgs = chunk.getItems().stream()
                .map(stats -> {
                    ContentPost contentPost = stats.getContentPost();

                    // 누적 시청 시간 업데이트
                    contentPost.addWatchTime(stats.getWatchTime());

                    return new Object[]{
                            contentPost.getId(),
                            stats.getStatisticsDate(),
                            stats.getPeriod().name(),
                            stats.getViewCount(),
                            stats.getWatchTime(),
                            contentPost.getTotalViews()
                    };
                }).toList();

        // Batch 업데이트
        jdbcTemplate.batchUpdate(INSERT_OR_UPDATE_SQL, batchArgs);

        log.debug("Processed {} statistics entries", chunk.getItems().size());
    }
}
