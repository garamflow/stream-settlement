package com.github.garamflow.streamsettlement.repository.statistics;

import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
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
    private final EntityManager entityManager;

    @Override
    public List<ContentStatistics> findByDateAndPeriod(LocalDate date, StatisticsPeriod period) {
        return jpaQueryFactory
                .selectFrom(contentStatistics)
                .where(periodAndDateEq(period, date))
                .fetch();
    }

    @Override
    public List<ContentStatistics> findAllByStatisticsDateAndPeriodWithFetch(LocalDate statisticsDate, StatisticsPeriod period) {
        return entityManager.createQuery(
                        "SELECT cs FROM ContentStatistics cs " +
                                "JOIN FETCH cs.contentPost " +
                                "WHERE cs.statisticsDate = :statisticsDate " +
                                "AND cs.period = :period", ContentStatistics.class)
                .setParameter("statisticsDate", statisticsDate)
                .setParameter("period", period)
                .getResultList();
    }

    @Override
    public long findMinIdByStatisticsDate(LocalDate date) {
        Long result = jpaQueryFactory
                .select(contentStatistics.id.min())
                .from(contentStatistics)
                .where(contentStatistics.statisticsDate.eq(date))
                .fetchOne();
        return result != null ? result : 0L;
    }

    @Override
    public long findMaxIdByStatisticsDate(LocalDate date) {
        Long result = jpaQueryFactory
                .select(contentStatistics.id.max())
                .from(contentStatistics)
                .where(contentStatistics.statisticsDate.eq(date))
                .fetchOne();
        return result != null ? result : 0L;
    }

    @Override
    public List<ContentStatistics> findByIdBetweenAndStatisticsDate(
            Long startId, Long endId, LocalDate date) {
        return jpaQueryFactory
                .selectFrom(contentStatistics)
                .where(
                        contentStatistics.id.between(startId, endId),
                        contentStatistics.statisticsDate.eq(date)
                )
                .orderBy(contentStatistics.id.asc())
                .fetch();
    }

    @Override
    public List<ContentStatistics> findByContentPostIdAndStatisticsDateBetween(
            Long contentPostId, LocalDate startDate, LocalDate endDate) {
        return jpaQueryFactory
                .selectFrom(contentStatistics)
                .where(
                        contentPostIdEq(contentPostId),
                        betweenDates(startDate, endDate)
                )
                .fetch();
    }

    @Override
    public List<ContentStatistics> findTop5ByViewCount(StatisticsPeriod period, LocalDate date) {
        return jpaQueryFactory
                .selectFrom(contentStatistics)
                .where(
                        periodAndDateEq(period, date)  // 중복 조건을 메소드로 추출
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
                        periodAndDateEq(period, date)  // 중복 조건을 메소드로 추출
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


    private BooleanExpression periodAndDateEq(StatisticsPeriod period, LocalDate date) {
        return contentStatistics.period.eq(period)
                .and(contentStatistics.statisticsDate.eq(date));
    }

    private BooleanExpression betweenDates(LocalDate startDate, LocalDate endDate) {
        return contentStatistics.statisticsDate.between(startDate, endDate);
    }

    private BooleanExpression contentPostIdEq(Long contentPostId) {
        return contentPostId != null ? contentStatistics.contentPost.id.eq(contentPostId) : null;
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
} 