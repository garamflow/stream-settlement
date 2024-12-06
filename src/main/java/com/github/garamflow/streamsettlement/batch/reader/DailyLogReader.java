package com.github.garamflow.streamsettlement.batch.reader;

import com.github.garamflow.streamsettlement.batch.reader.mapper.DailyMemberViewLogRowMapper;
import com.github.garamflow.streamsettlement.entity.stream.Log.DailyMemberViewLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.support.MySqlPagingQueryProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;


@Slf4j
@Component
@RequiredArgsConstructor
public class DailyLogReader {

    private final DailyMemberViewLogRowMapper rowMapper;
    private final DataSource dataSource;
    private static final int CHUNK_SIZE = 1000;

    @Bean
    @StepScope
    public JdbcPagingItemReader<DailyMemberViewLog> reader(
            @Value("#{jobParameters['targetDate']}") String targetDateStr,
            @Value("#{stepExecutionContext['minId']}") Long minId,
            @Value("#{stepExecutionContext['maxId']}") Long maxId) {

        // 유효성 검사
        if (minId == null || maxId == null) {
            throw new IllegalStateException("StepExecutionContext missing 'minId' or 'maxId'.");
        }

        LocalDate targetDate = validateAndParseDate(targetDateStr);
        MySqlPagingQueryProvider queryProvider = createQueryProvider();

        log.info("Reader initialized for targetDate: {}, minId: {}, maxId: {}", targetDate, minId, maxId);

        return new JdbcPagingItemReaderBuilder<DailyMemberViewLog>()
                .name("dailyLogReader")
                .dataSource(dataSource)
                .pageSize(CHUNK_SIZE)
                .fetchSize(CHUNK_SIZE)
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

    private LocalDate validateAndParseDate(String targetDateStr) {
        if (targetDateStr == null || targetDateStr.isBlank()) {
            throw new IllegalArgumentException("Job parameter 'targetDate' is required but not provided.");
        }

        try {
            return LocalDate.parse(targetDateStr);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid 'targetDate' format. Expected format is yyyy-MM-dd.", e);
        }
    }
}