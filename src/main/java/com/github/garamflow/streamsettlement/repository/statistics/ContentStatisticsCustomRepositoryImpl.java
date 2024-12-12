package com.github.garamflow.streamsettlement.repository.statistics;

import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static com.github.garamflow.streamsettlement.entity.statistics.QContentStatistics.contentStatistics;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ContentStatisticsCustomRepositoryImpl implements ContentStatisticsCustomRepository {
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final JPAQueryFactory jpaQueryFactory;

    @Override
    public List<ContentStatistics> findByDateAndPeriod(LocalDate date, StatisticsPeriod period) {
        return jpaQueryFactory
                .selectFrom(contentStatistics)
                .where(
                        contentStatistics.statisticsDate.eq(date),
                        contentStatistics.period.eq(period)
                )
                .fetch();
    }

    @Override
    public List<ContentStatistics> findTop5ByViewCount(StatisticsPeriod period, LocalDate date) {
        return jpaQueryFactory
                .selectFrom(contentStatistics)
                .where(
                        contentStatistics.period.eq(period),
                        contentStatistics.statisticsDate.eq(date)
                )
                .orderBy(contentStatistics.viewCount.desc())
                .limit(5)
                .fetch();
    }

    @Override
    public List<ContentStatistics> findTop5ByWatchTime(StatisticsPeriod period, LocalDate date) {
        return jpaQueryFactory
                .selectFrom(contentStatistics)
                .where(
                        contentStatistics.period.eq(period),
                        contentStatistics.statisticsDate.eq(date)
                )
                .orderBy(contentStatistics.watchTime.desc())
                .limit(5)
                .fetch();
    }


    public List<ContentStatistics> findStatisticsByCondition(Long cursorId, LocalDate statisticsDate, StatisticsPeriod period, Long fetchSize) {
        return jpaQueryFactory
                .select(contentStatistics)
                .from(contentStatistics)
                .where(
                        statisticsDateEq(statisticsDate),
                        periodEq(period),
                        cursorCondition(cursorId)
                )
                .orderBy(contentStatistics.id.asc())
                .limit(fetchSize)
                .fetch();
    }

    private BooleanExpression statisticsDateEq(LocalDate date) {
        return contentStatistics.statisticsDate.eq(date);
    }

    private BooleanExpression periodEq(StatisticsPeriod period) {
        return contentStatistics.period.eq(period);
    }

    private BooleanExpression cursorCondition(Long cursorId) {
        return cursorId == null ? null : contentStatistics.id.goe(cursorId);
    }

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