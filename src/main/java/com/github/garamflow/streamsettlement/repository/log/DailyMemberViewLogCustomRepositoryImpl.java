package com.github.garamflow.streamsettlement.repository.log;

import com.github.garamflow.streamsettlement.entity.stream.Log.DailyMemberViewLog;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.support.MySqlPagingQueryProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class DailyMemberViewLogCustomRepositoryImpl implements DailyMemberViewLogCustomRepository {

    private final DataSource dataSource;
    private final DailyMemberViewLogMapper rowMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public Map<String, Long> getPartitionInfo(LocalDate targetDate) {
        String sql = """
                SELECT COALESCE(MIN(daily_member_view_log_id), 0) as min_id,
                       COALESCE(MAX(daily_member_view_log_id), 0) as max_id,
                       COUNT(*) as total_count
                FROM daily_member_view_log
                WHERE log_date = ?
                AND last_viewed_position > 0
                """;

        Map<String, Object> result = jdbcTemplate.queryForMap(sql, targetDate);
        Map<String, Long> partitionInfo = new HashMap<>();
        partitionInfo.put("minId", ((Number) result.get("min_id")).longValue());
        partitionInfo.put("maxId", ((Number) result.get("max_id")).longValue());
        partitionInfo.put("totalCount", ((Number) result.get("total_count")).longValue());

        return partitionInfo;
    }

    @Override
    public JdbcPagingItemReader<DailyMemberViewLog> createPagingReader(
            LocalDate targetDate, Long minId, Long maxId, int pageSize) {

        MySqlPagingQueryProvider queryProvider = createQueryProvider();

        return new JdbcPagingItemReaderBuilder<DailyMemberViewLog>()
                .name("dailyLogReader")
                .dataSource(dataSource)
                .pageSize(pageSize)
                .fetchSize(pageSize)
                .rowMapper(rowMapper)
                .queryProvider(queryProvider)
                .parameterValues(Map.of(
                        "targetDate", targetDate,
                        "minId", minId,
                        "maxId", maxId
                ))
                .build();
    }

    private MySqlPagingQueryProvider createQueryProvider() {
        MySqlPagingQueryProvider queryProvider = new MySqlPagingQueryProvider();
        queryProvider.setSelectClause("""
                d.daily_member_view_log_id, d.last_viewed_position, d.last_ad_view_count,
                d.log_date, d.streaming_status,
                m.member_id,
                c.content_post_id
                """);
        queryProvider.setFromClause("""
                daily_member_view_log d
                JOIN member m ON d.member_id = m.member_id
                JOIN content_post c ON d.content_post_id = c.content_post_id
                """);
        queryProvider.setWhereClause("""
                d.log_date = :targetDate
                AND d.daily_member_view_log_id BETWEEN :minId AND :maxId
                AND d.last_viewed_position > 0
                """);
        queryProvider.setSortKeys(Map.of("d.daily_member_view_log_id", Order.ASCENDING));
        return queryProvider;
    }
}