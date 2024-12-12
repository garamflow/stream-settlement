package com.github.garamflow.streamsettlement.repository.log;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

import static com.github.garamflow.streamsettlement.entity.stream.Log.QDailyWatchedContent.dailyWatchedContent;


@Repository
@RequiredArgsConstructor
public class DailyWatchedContentQueryRepository {
    private final JPAQueryFactory queryFactory;

    public List<Long> findContentIdsByWatchedDate(LocalDate watchedDate, Long cursorId, Long size) {
        return queryFactory
                .select(dailyWatchedContent.contentPostId)
                .from(dailyWatchedContent)
                .where(
                        watchedDateEq(watchedDate),
                        cursorIdGoe(cursorId)
                )
                .orderBy(dailyWatchedContent.id.asc())
                .limit(size)
                .fetch();
    }

    public Long findMinIdByWatchedDate(LocalDate watchedDate) {
        return queryFactory
                .select(dailyWatchedContent.id.min())
                .from(dailyWatchedContent)
                .where(watchedDateEq(watchedDate))
                .fetchOne();
    }

    public Long findMaxIdByWatchedDate(LocalDate watchedDate) {
        return queryFactory
                .select(dailyWatchedContent.id.max())
                .from(dailyWatchedContent)
                .where(watchedDateEq(watchedDate))
                .fetchOne();
    }

    private BooleanExpression watchedDateEq(LocalDate date) {
        return date != null ? dailyWatchedContent.watchedDate.eq(date) : null;
    }

    private BooleanExpression cursorIdGoe(Long cursorId) {
        return cursorId != null ? dailyWatchedContent.id.goe(cursorId) : null;
    }
}