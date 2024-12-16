package com.github.garamflow.streamsettlement.repository.statistics;

import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ContentStatisticsCustomRepositoryImpl implements ContentStatisticsCustomRepository {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    @Transactional
    public void bulkInsertStatistics(List<ContentStatistics> statistics) {
        String sql = """
                INSERT INTO content_statistics
                (content_post_id, statistics_date, period, view_count, watch_time, accumulated_views)
                VALUES (:contentPostId, :statisticsDate, :period, :viewCount, :watchTime, :accumulatedViews)
                ON DUPLICATE KEY UPDATE
                    view_count = view_count + VALUES(view_count),
                    watch_time = watch_time + VALUES(watch_time),
                    accumulated_views = GREATEST(accumulated_views, VALUES(accumulated_views))
                """;

        namedParameterJdbcTemplate.batchUpdate(sql, getStatisticsParameterSources(statistics));
    }

    private MapSqlParameterSource[] getStatisticsParameterSources(List<ContentStatistics> statistics) {
        return statistics.stream()
                .map(this::getStatisticsParameterSource)
                .toArray(MapSqlParameterSource[]::new);
    }

    private MapSqlParameterSource getStatisticsParameterSource(ContentStatistics stat) {
        return new MapSqlParameterSource()
                .addValue("contentPostId", stat.getContentPost().getId())
                .addValue("statisticsDate", stat.getStatisticsDate())
                .addValue("period", stat.getPeriod().name())
                .addValue("viewCount", stat.getViewCount())
                .addValue("watchTime", stat.getWatchTime())
                .addValue("accumulatedViews", stat.getAccumulatedViews());
    }
}
