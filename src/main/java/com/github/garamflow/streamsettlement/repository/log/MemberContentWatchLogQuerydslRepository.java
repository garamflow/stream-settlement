package com.github.garamflow.streamsettlement.repository.log;

import com.github.garamflow.streamsettlement.entity.stream.Log.MemberContentWatchLog;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.github.garamflow.streamsettlement.entity.stream.Log.QMemberContentWatchLog.memberContentWatchLog;

@Repository
@RequiredArgsConstructor
public class MemberContentWatchLogQuerydslRepository {

    private final JPAQueryFactory jpaQueryFactory;

    public List<MemberContentWatchLog> findByContentPostIdAndWatchedDateWithPaging(
            Long contentPostId, LocalDate watchedDate, Long cursorId, Long fetchSize) {
        return jpaQueryFactory
                .selectFrom(memberContentWatchLog)
                .where(
                        memberContentWatchLog.contentPostId.eq(contentPostId),
                        memberContentWatchLog.watchedDate.eq(watchedDate),
                        cursorIdCondition(cursorId)
                )
                .orderBy(memberContentWatchLog.id.asc())
                .limit(fetchSize)
                .fetch();
    }

    public Set<Long> findContentIdsByDate(LocalDate date) {
        List<Long> contentIds = jpaQueryFactory
                .select(memberContentWatchLog.contentPostId)
                .from(memberContentWatchLog)
                .where(watchedDateEq(date))
                .fetch();

        return new HashSet<>(contentIds);
    }

    private BooleanExpression cursorIdCondition(Long cursorId) {
        return cursorId != null ? memberContentWatchLog.id.gt(cursorId) : null;
    }

    private BooleanExpression watchedDateEq(LocalDate date) {
        return date != null ? memberContentWatchLog.watchedDate.eq(date) : null;
    }
}

