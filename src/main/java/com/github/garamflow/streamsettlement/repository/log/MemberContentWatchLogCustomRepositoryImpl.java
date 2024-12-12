package com.github.garamflow.streamsettlement.repository.log;

import com.github.garamflow.streamsettlement.entity.stream.Log.DailyWatchedContent;
import com.github.garamflow.streamsettlement.entity.stream.Log.MemberContentWatchLog;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.support.MySqlPagingQueryProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.*;

import static com.github.garamflow.streamsettlement.entity.stream.Log.QDailyWatchedContent.dailyWatchedContent;
import static com.github.garamflow.streamsettlement.entity.stream.Log.QMemberContentWatchLog.memberContentWatchLog;

@Repository
@RequiredArgsConstructor
public class MemberContentWatchLogCustomRepositoryImpl implements MemberContentWatchLogCustomRepository {
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    private final JPAQueryFactory queryFactory;


    @Override
    public Map<String, Long> getPartitionInfo(LocalDate targetDate) {
        String sql = """
                SELECT COALESCE(MIN(id), 0) as min_id,
                       COALESCE(MAX(id), 0) as max_id,
                       COUNT(*) as total_count
                FROM member_content_watch_log
                WHERE log_date = ?
                """;

        Map<String, Object> result = jdbcTemplate.queryForMap(sql, targetDate);
        Map<String, Long> partitionInfo = new HashMap<>();
        partitionInfo.put("minId", ((Number) result.get("min_id")).longValue());
        partitionInfo.put("maxId", ((Number) result.get("max_id")).longValue());
        partitionInfo.put("totalCount", ((Number) result.get("total_count")).longValue());

        return partitionInfo;
    }

    @Override
    public Set<Long> findContentIdsByDate(LocalDate date) {
        List<Long> contentIds = queryFactory
                .select(memberContentWatchLog.contentPostId)  // contentPostId만 선택
                .from(memberContentWatchLog)
                .where(watchedDateEq(date))
                .fetch();

        return new HashSet<>(contentIds);
    }

    @Override
    public JdbcPagingItemReader<MemberContentWatchLog> createPagingReader(
            LocalDate targetDate, Long minId, Long maxId, int pageSize) {
        MySqlPagingQueryProvider queryProvider = new MySqlPagingQueryProvider();
        queryProvider.setSelectClause("""
                d.id, d.log_date, d.streaming_status,
                c.content_post_id
                """);
        queryProvider.setFromClause("""
                daily_watched_contents d
                JOIN content_posts c ON d.content_post_id = c.content_post_id
                """);
        queryProvider.setWhereClause("d.log_date = :targetDate AND d.content_post_id IN (:contentIds)");
        queryProvider.setSortKeys(Map.of("d.content_post_id", Order.ASCENDING));

        return new JdbcPagingItemReaderBuilder<MemberContentWatchLog>()
                .name("dailyWatchedContentReader")
                .dataSource(dataSource)
                .pageSize(pageSize)
                .fetchSize(pageSize)
                .queryProvider(queryProvider)
                .parameterValues(Map.of(
                        "targetDate", targetDate,
                        "contentIds", getContentIdsFromContext()
                ))
                .build();
    }

    public List<DailyWatchedContent> findByLogDateAndIdGreaterThan(
            LocalDate logDate, Long cursorId, Long limit) {
        return queryFactory
                .selectFrom(dailyWatchedContent)
                .where(
                        dailyWatchedContent.watchedDate.eq(logDate),
                        dailyWatchedContent.id.gt(cursorId != null ? cursorId : 0)
                )
                .orderBy(dailyWatchedContent.id.asc())
                .limit(limit)
                .fetch();
    }

    public List<DailyWatchedContent> findByLogDateAndContentPostIdsAndIdGreaterThan(
            LocalDate logDate, Set<Long> contentPostIds, Long cursorId, Long limit) {
        return queryFactory
                .selectFrom(dailyWatchedContent)
                .where(
                        dailyWatchedContent.watchedDate.eq(logDate),
                        dailyWatchedContent.contentPostId.in(contentPostIds),
                        dailyWatchedContent.id.gt(cursorId != null ? cursorId : 0)
                )
                .orderBy(dailyWatchedContent.id.asc())
                .limit(limit)
                .fetch();
    }

    private List<Long> getContentIdsFromContext() {
        @SuppressWarnings("unchecked")
        List<Long> contentIds = (List<Long>) Objects.requireNonNull(StepSynchronizationManager.getContext())
                .getStepExecution()
                .getExecutionContext()
                .get("contentIds");
        return contentIds;
    }

    private BooleanExpression watchedDateEq(LocalDate date) {
        return date != null ? memberContentWatchLog.watchedDate.eq(date) : null;
    }
}