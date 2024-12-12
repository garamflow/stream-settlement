package com.github.garamflow.streamsettlement.batch.reader;

import com.github.garamflow.streamsettlement.batch.config.BatchProperties;
import com.github.garamflow.streamsettlement.entity.stream.Log.MemberContentWatchLog;
import com.github.garamflow.streamsettlement.entity.stream.Log.StreamingStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;


@Slf4j
@Component
@RequiredArgsConstructor
public class DailyLogReader implements ItemReader<MemberContentWatchLog> {

    private JdbcPagingItemReader<MemberContentWatchLog> delegate;

    private final JdbcTemplate jdbcTemplate;
    private final BatchProperties batchProperties;

    @Bean
    @StepScope
    public JdbcPagingItemReader<MemberContentWatchLog> createDelegate(
            @Value("#{jobParameters['targetDate']}") String targetDateStr,
            @Value("#{stepExecutionContext['startContentId']}") Long startContentId,
            @Value("#{stepExecutionContext['endContentId']}") Long endContentId) {

        if (startContentId == null || endContentId == null) {
            throw new IllegalStateException("StepExecutionContext missing 'startContentId' or 'endContentId'.");
        }

        LocalDate targetDate = validateAndParseDate(targetDateStr);
        log.info("Reader initialized for targetDate: {}, startContentId: {}, endContentId: {}",
                targetDate, startContentId, endContentId);

        delegate = new JdbcPagingItemReaderBuilder<MemberContentWatchLog>()
                .name("dailyLogReader")
                .dataSource(Objects.requireNonNull(jdbcTemplate.getDataSource()))
                .selectClause("SELECT *")
                .fromClause("FROM member_content_watch_log")
                .whereClause("WHERE watched_date = :date AND content_post_id BETWEEN :start AND :end")
                .parameterValues(Map.of(
                        "date", targetDate,
                        "start", startContentId,
                        "end", endContentId
                ))
                .sortKeys(Map.of(
                        "content_post_id", Order.ASCENDING,
                        "member_id", Order.ASCENDING
                ))
                .rowMapper(new MemberContentWatchLogRowMapper())
                .pageSize(batchProperties.getChunkSize())
                .build();

        return delegate;
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

    @Override
    public MemberContentWatchLog read() throws Exception {
        MemberContentWatchLog watchLog = delegate.read();
        if (watchLog != null) {
            log.info("Read log: contentPostId={}, memberId={}, watchedDate={}",
                    watchLog.getContentPostId(), watchLog.getMemberId(), watchLog.getWatchedDate());
        }
        return watchLog;
    }

    private static class MemberContentWatchLogRowMapper implements RowMapper<MemberContentWatchLog> {
        @Override
        public MemberContentWatchLog mapRow(ResultSet rs, int rowNum) throws SQLException {
            return MemberContentWatchLog.customBuilder()
                .memberId(rs.getLong("member_id"))
                .contentPostId(rs.getLong("content_post_id"))
                .lastPlaybackPosition(rs.getLong("last_playback_position"))
                .totalPlaybackTime(rs.getLong("total_playback_time"))
                .watchedDate(rs.getDate("watched_date").toLocalDate())
                .streamingStatus(StreamingStatus.valueOf(rs.getString("streaming_status")))
                .build();
        }
    }
}