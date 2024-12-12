package com.github.garamflow.streamsettlement.repository.advertisement;

import com.github.garamflow.streamsettlement.entity.stream.Log.MemberAdWatchLog;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

import static com.github.garamflow.streamsettlement.entity.stream.Log.QMemberAdWatchLog.memberAdWatchLog;


@Repository
@RequiredArgsConstructor
public class MemberAdWatchLogQueryRepository {

    private final JPAQueryFactory queryFactory;

    public List<MemberAdWatchLog> findUserAdWatchHistoriesByCondition(Long contentId, Long cursorId, LocalDate date, Long fetchSize) {
        return queryFactory
                .select(memberAdWatchLog)
                .from(memberAdWatchLog)
                .where(
                        memberAdWatchLog.contentPostId.eq(contentId),
                        memberAdWatchLog.watchedDate.eq(date),
                        cursorCondition(cursorId)
                )
                .limit(fetchSize)
                .fetch();
    }

    private BooleanExpression cursorCondition(Long cursorId) {
        return cursorId == null ? null : memberAdWatchLog.id.gt(cursorId);
    }
}
