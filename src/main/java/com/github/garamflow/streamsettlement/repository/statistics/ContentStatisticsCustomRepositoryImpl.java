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

    /**
     * 컨텐츠 통계 데이터 목록을 데이터베이스에 벌크 삽입합니다.
     * 동일한 키가 있는 경우 조회수와 시청시간은 누적하고, 누적 조회수는 최대값을 유지합니다.
     *
     * @param items 삽입할 통계 데이터 목록
     */
    @Override
    @Transactional
    public void bulkInsert(List<ContentStatistics> items) {
        String sql = """
                INSERT INTO content_statistics
                (content_post_id, statistics_date, period, view_count, watch_time, accumulated_views)
                VALUES (:contentPostId, :statisticsDate, :period, :viewCount, :watchTime, :accumulatedViews)
                ON DUPLICATE KEY UPDATE
                    view_count = view_count + VALUES(view_count),
                    watch_time = watch_time + VALUES(watch_time),
                    accumulated_views = GREATEST(accumulated_views, VALUES(accumulated_views))
                """;

        namedParameterJdbcTemplate.batchUpdate(sql, getStatisticsParameterSources(items));
    }

    /**
     * 통계 데이터 목록을 SQL 파라미터 배열로 변환합니다.
     *
     * @param statistics 변환할 통계 데이터 목록
     * @return SQL 파라미터 배열
     */
    private MapSqlParameterSource[] getStatisticsParameterSources(List<ContentStatistics> statistics) {
        return statistics.stream()
                .map(this::getStatisticsParameterSource)
                .toArray(MapSqlParameterSource[]::new);
    }

    /**
     * 단일 통계 데이터를 SQL 파라미터로 변환합니다.
     *
     * @param stat 변환할 통계 데이터
     * @return SQL 파라미터
     */
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
