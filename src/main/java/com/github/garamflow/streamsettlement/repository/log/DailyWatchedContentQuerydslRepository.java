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
import static com.querydsl.core.types.ExpressionUtils.count;
import static com.querydsl.core.types.dsl.Expressions.numberTemplate;


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
            Long lastContentId,
            LocalDate watchedDate,
            int limit
    ) {
        return jpaQueryFactory
                .select(Projections.constructor(CumulativeStatisticsDto.class,
                        dailyWatchedContent.contentPostId,
                        count(memberContentWatchLog.id),
                        numberTemplate(Long.class, "COALESCE(sum({0}), 0)", memberContentWatchLog.totalPlaybackTime),
                        count(numberTemplate(Long.class, "distinct {0}", memberContentWatchLog.memberId))))
                .from(dailyWatchedContent)
                .leftJoin(memberContentWatchLog)
                .on(dailyWatchedContent.contentPostId.eq(memberContentWatchLog.contentPostId)
                        .and(memberContentWatchLog.watchedDate.eq(watchedDate)))
                .where(
                        watchedDateEq(watchedDate),
                        contentIdGt(lastContentId)
                )
                .groupBy(dailyWatchedContent.contentPostId)
                .orderBy(dailyWatchedContent.contentPostId.asc())
                .limit(limit)
                .fetch();
    }

    private BooleanExpression contentIdGt(Long contentId) {
        return contentId != null ? dailyWatchedContent.contentPostId.gt(contentId) : null;
    }

    private BooleanExpression watchedDateEq(LocalDate date) {
        return date != null ? dailyWatchedContent.watchedDate.eq(date) : null;
    }
}