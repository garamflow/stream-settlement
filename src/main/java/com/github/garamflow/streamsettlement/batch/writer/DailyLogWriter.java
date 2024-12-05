package com.github.garamflow.streamsettlement.batch.writer;

import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyLogWriter implements ItemWriter<List<ContentStatistics>> {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private static final int BATCH_SIZE = 500;

    private static final String INSERT_SQL = """
            INSERT IGNORE INTO content_statistics
            (content_post_id, statistics_date, period, view_count, watch_time, accumulated_views)
            VALUES (:contentPostId, :statisticsDate, :period, :viewCount, :watchTime, :accumulatedViews)
            """;

    private static final String UPDATE_SQL = """
            UPDATE content_statistics
            SET view_count = view_count + :viewCount,
                watch_time = watch_time + :watchTime,
                accumulated_views = :accumulatedViews
            WHERE content_post_id = :contentPostId 
              AND statistics_date = :statisticsDate 
              AND period = :period
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

        List<SqlParameterSource> batchParams = allStats.stream()
                .map(this::createSqlParameterSource)
                .toList();

        try {
            jdbcTemplate.batchUpdate(INSERT_SQL, batchParams.toArray(new SqlParameterSource[0]));
        } catch (Exception e) {
            log.error("Error during bulk insert", e);
            throw new RuntimeException("Bulk insert failed", e);
        }
    }

    private SqlParameterSource createSqlParameterSource(ContentStatistics stats) {
        return new MapSqlParameterSource()
                .addValue("contentPostId", stats.getContentPost().getId())
                .addValue("statisticsDate", stats.getStatisticsDate())
                .addValue("period", stats.getPeriod().name())
                .addValue("viewCount", stats.getViewCount())
                .addValue("watchTime", stats.getWatchTime())
                .addValue("accumulatedViews", stats.getAccumulatedViews());
    }
}