package com.github.garamflow.streamsettlement.repository.statistics;

import com.github.garamflow.streamsettlement.batch.dto.CumulativeStatisticsDto;
import com.github.garamflow.streamsettlement.batch.dto.QCumulativeStatisticsDto;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

import static com.github.garamflow.streamsettlement.entity.statistics.QContentStatistics.contentStatistics;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ContentStatisticsQuerydslRepository {

    private final JPAQueryFactory jpaQueryFactory;

    public long findMinIdByStatisticsDate(LocalDate date) {
        Long result = jpaQueryFactory
                .select(contentStatistics.id.min())
                .from(contentStatistics)
                .where(dateEq(date))
                .fetchOne();
        return result != null ? result : 0L;
    }

    public long findMaxIdByStatisticsDate(LocalDate date) {
        Long result = jpaQueryFactory
                .select(contentStatistics.id.max())
                .from(contentStatistics)
                .where(dateEq(date))
                .fetchOne();
        return result != null ? result : 0L;
    }

    /**
     * Zero-Offset 방식의 통계 데이터 조회
     */
    public List<CumulativeStatisticsDto> findDailyStatisticsByContentIdGreaterThan(
            Long lastContentId,
            LocalDate targetDate,
            int limit) {

        List<CumulativeStatisticsDto> results = jpaQueryFactory
                .select(new QCumulativeStatisticsDto(
                        contentStatistics.contentPost.id,
                        contentStatistics.viewCount,
                        contentStatistics.watchTime,
                        contentStatistics.accumulatedViews
                ))
                .from(contentStatistics)
                .where(
                        contentStatistics.statisticsDate.eq(targetDate),
                        lastContentId == null ? null : contentStatistics.contentPost.id.gt(lastContentId)
                )
                .orderBy(contentStatistics.contentPost.id.asc())
                .limit(limit)
                .fetch();

        log.debug("Zero-Offset query executed - lastContentId: {}, results: {}",
                lastContentId, results.size());

        return results;
    }

    /**
     * Zero-Offset 방식의 정산용 통계 데이터 조회
     */
    public List<ContentStatistics> findByIdGreaterThanAndStatisticsDate(
            Long lastStatisticsId,
            LocalDate targetDate,
            int limit) {

        return jpaQueryFactory
                .selectFrom(contentStatistics)
                .where(
                        contentStatistics.statisticsDate.eq(targetDate),
                        lastStatisticsId == null ? null : contentStatistics.id.gt(lastStatisticsId)
                )
                .orderBy(contentStatistics.id.asc())
                .limit(limit)
                .fetch();
    }

    public List<ContentStatistics> findTop5ByViewCount(StatisticsPeriod period, LocalDate date) {
        return jpaQueryFactory
                .selectFrom(contentStatistics)
                .where(periodEq(period), dateEq(date))
                .orderBy(contentStatistics.viewCount.desc())
                .limit(5)
                .fetch();
    }

    public List<ContentStatistics> findTop5ByWatchTime(StatisticsPeriod period, LocalDate date) {
        return jpaQueryFactory
                .selectFrom(contentStatistics)
                .where(periodEq(period), dateEq(date))
                .orderBy(contentStatistics.watchTime.desc())
                .limit(5)
                .fetch();
    }

    /**
     * 기간(From~To)과 기간타입에 해당하는 통계 조회
     */
    public List<ContentStatistics> findByStatisticsDateBetweenAndPeriod(
            LocalDate startDate, LocalDate endDate, StatisticsPeriod period) {
        return jpaQueryFactory
                .selectFrom(contentStatistics)
                .where(
                        betweenDates(startDate, endDate),
                        periodEq(period)
                )
                .fetch();
    }

    /**
     * 기간(From~To)와 기간타입에 해당하는 통계 중 viewCount 상위 5개 조회
     */
    public List<ContentStatistics> findTop5ByViewCountBetweenAndPeriod(
            LocalDate startDate, LocalDate endDate, StatisticsPeriod period) {
        return jpaQueryFactory
                .selectFrom(contentStatistics)
                .where(
                        betweenDates(startDate, endDate),
                        periodEq(period)
                )
                .orderBy(contentStatistics.viewCount.desc())
                .limit(5)
                .fetch();
    }

    /**
     * 기간(From~To)와 기간타입에 해당하는 통계 중 watchTime 상위 5개 조회
     */
    public List<ContentStatistics> findTop5ByWatchTimeBetweenAndPeriod(
            LocalDate startDate, LocalDate endDate, StatisticsPeriod period) {
        return jpaQueryFactory
                .selectFrom(contentStatistics)
                .where(
                        betweenDates(startDate, endDate),
                        periodEq(period)
                )
                .orderBy(contentStatistics.watchTime.desc())
                .limit(5)
                .fetch();
    }

    // --- BooleanExpression 헬퍼 메서드 ---
    private BooleanExpression periodEq(StatisticsPeriod period) {
        return contentStatistics.period.eq(period);
    }

    private BooleanExpression dateEq(LocalDate date) {
        return contentStatistics.statisticsDate.eq(date);
    }

    private BooleanExpression betweenDates(LocalDate startDate, LocalDate endDate) {
        return contentStatistics.statisticsDate.between(startDate, endDate);
    }
}

