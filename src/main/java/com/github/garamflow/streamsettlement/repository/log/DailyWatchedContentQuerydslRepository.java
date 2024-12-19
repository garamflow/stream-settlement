package com.github.garamflow.streamsettlement.repository.log;

import com.github.garamflow.streamsettlement.batch.dto.CumulativeStatisticsDto;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

import static com.github.garamflow.streamsettlement.entity.stream.Log.QDailyWatchedContent.dailyWatchedContent;
import static com.github.garamflow.streamsettlement.entity.stream.Log.QMemberContentWatchLog.memberContentWatchLog;


@Repository
@RequiredArgsConstructor
public class DailyWatchedContentQuerydslRepository {
    private final JPAQueryFactory jpaQueryFactory;

    public Long findMinIdByWatchedDate(LocalDate watchedDate) {
        return jpaQueryFactory
                .select(dailyWatchedContent.id.min())
                .from(dailyWatchedContent)
                .where(watchedDateEq(watchedDate))
                .fetchOne();
    }

    public Long findMaxIdByWatchedDate(LocalDate watchedDate) {
        return jpaQueryFactory
                .select(dailyWatchedContent.id.max())
                .from(dailyWatchedContent)
                .where(watchedDateEq(watchedDate))
                .fetchOne();
    }

    public List<CumulativeStatisticsDto> findDailyWatchedContentForStatistics(
            List<Long> contentIds,
            LocalDate targetDate) {
        return jpaQueryFactory
                .select(Projections.constructor(CumulativeStatisticsDto.class,
                        memberContentWatchLog.id.min(),
                        memberContentWatchLog.contentPostId,
                        memberContentWatchLog.id.count(),
                        memberContentWatchLog.totalPlaybackTime.sum(),
                        memberContentWatchLog.watchedDate))
                .from(memberContentWatchLog)
                .where(
                        memberContentWatchLog.contentPostId.in(contentIds),
                        memberContentWatchLog.watchedDate.eq(targetDate)
                )
                .groupBy(
                        memberContentWatchLog.contentPostId,
                        memberContentWatchLog.watchedDate
                )
                .fetch();
    }

    public List<Long> findContentIdsByWatchedDate(LocalDate date, Long lastContentId, int limit) {
        return jpaQueryFactory
                .select(dailyWatchedContent.contentPostId)
                .from(dailyWatchedContent)
                .where(
                        dailyWatchedContent.watchedDate.eq(date),
                        lastContentId == null ? null : dailyWatchedContent.contentPostId.gt(lastContentId)
                )
                .orderBy(dailyWatchedContent.contentPostId.asc())
                .limit(limit)
                .fetch();
    }

    private BooleanExpression watchedDateEq(LocalDate date) {
        return date != null ? dailyWatchedContent.watchedDate.eq(date) : null;
    }
}