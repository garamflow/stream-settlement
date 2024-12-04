package com.github.garamflow.streamsettlement.batch.writer;

import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.data.util.Pair;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class DailyLogWriter implements ItemWriter<List<ContentStatistics>> {

    private final JdbcTemplate jdbcTemplate;

    private static final String INSERT_OR_UPDATE_SQL = """
            INSERT INTO content_statistics
            (content_post_id, statistics_date, period, view_count, watch_time, accumulated_views)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
            view_count = view_count + VALUES(view_count),
            watch_time = watch_time + VALUES(watch_time),
            accumulated_views = VALUES(accumulated_views)
            """;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void write(Chunk<? extends List<ContentStatistics>> chunk) {
        if (chunk.isEmpty()) {
            return;
        }

        List<ContentStatistics> allStats = chunk.getItems().stream()
                .flatMap(List::stream)
                .sorted(Comparator.comparing((ContentStatistics stats) -> stats.getContentPost().getId())
                        .thenComparing(ContentStatistics::getStatisticsDate))
                .toList();

        Map<Pair<Long, LocalDate>, List<ContentStatistics>> groupedStats =
                allStats.stream().collect(Collectors.groupingBy(
                        stats -> Pair.of(stats.getContentPost().getId(), stats.getStatisticsDate())
                ));

        for (Map.Entry<Pair<Long, LocalDate>, List<ContentStatistics>> entry : groupedStats.entrySet()) {
            saveStatisticsWithRetry(entry.getValue());
        }
    }

    @Retryable(
            retryFor = {CannotAcquireLockException.class},
            maxAttempts = 5,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    private void saveStatisticsWithRetry(List<ContentStatistics> statsGroup) {
        ContentPost contentPost = statsGroup.getFirst().getContentPost();
        LocalDate statisticsDate = statsGroup.getFirst().getStatisticsDate();

        long totalViews = statsGroup.stream().mapToLong(ContentStatistics::getViewCount).sum();
        long totalWatchTime = statsGroup.stream().mapToLong(ContentStatistics::getWatchTime).sum();

        // 일간, 주간, 월간 통계 순차적 저장
        for (StatisticsPeriod period : StatisticsPeriod.getAllPeriodsForDaily()) {
            saveStatistics(contentPost, statisticsDate, period, totalViews, totalWatchTime);
        }
    }

    @Retryable(
            retryFor = {CannotAcquireLockException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000)
    )
    private void saveStatistics(ContentPost contentPost, LocalDate date, StatisticsPeriod period,
                                long viewCount, long watchTime) {
        int updatedRows = jdbcTemplate.update(INSERT_OR_UPDATE_SQL,
                contentPost.getId(),
                date,
                period.name(),
                viewCount,
                watchTime,
                contentPost.getTotalViews()
        );

        if (updatedRows == 0) {
            log.warn("통계가 업데이트되지 않았습니다: 컨텐츠 ID={}, 날짜={}, 기간={}",
                    contentPost.getId(), date, period);
        }
    }
}