package com.github.garamflow.streamsettlement.service.statistics;

import com.github.garamflow.streamsettlement.controller.dto.stream.response.ContentStatisticsResponse;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import com.github.garamflow.streamsettlement.entity.stream.Log.MemberContentWatchLog;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import com.github.garamflow.streamsettlement.repository.log.MemberContentWatchLogRepository;
import com.github.garamflow.streamsettlement.repository.statistics.ContentStatisticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static java.time.temporal.TemporalAdjusters.previousOrSame;

@Service
@RequiredArgsConstructor
@Transactional
public class ContentStatisticsService {

    private final ContentStatisticsRepository contentStatisticsRepository;
    private final MemberContentWatchLogRepository memberContentWatchLogRepository;
    private final Long fetchSize = 1000L;

    public List<ContentStatisticsResponse> getTop5Views(StatisticsPeriod period) {
        LocalDate targetDate = getTargetDate(period);
        List<ContentStatistics> statistics = contentStatisticsRepository.findTop5ByViewCount(period, targetDate);

        return statistics.stream()
                .map(ContentStatisticsResponse::from)
                .toList();
    }

    public List<ContentStatisticsResponse> getTop5WatchTime(StatisticsPeriod period) {
        LocalDate targetDate = getTargetDate(period);
        List<ContentStatistics> statistics = contentStatisticsRepository.findTop5ByWatchTime(period, targetDate);

        return statistics.stream()
                .map(ContentStatisticsResponse::from)
                .toList();
    }

    private LocalDate getTargetDate(StatisticsPeriod period) {
        LocalDate now = LocalDate.now();
        return switch (period) {
            case DAILY -> now;
            case WEEKLY -> now.with(previousOrSame(DayOfWeek.MONDAY));
            case MONTHLY -> now.withDayOfMonth(1);
            case YEARLY -> now.withDayOfYear(1);
        };
    }

    public List<ContentStatistics> createDailyStatistics(ContentPost contentPost, LocalDate logDate) {
        validateContentPost(contentPost);

        List<ContentStatistics> statistics = new ArrayList<>();
        Long cursorId = null;

        while (true) {
            List<MemberContentWatchLog> watchLogs = fetchWatchLogs(contentPost.getId(), logDate, cursorId);
            if (watchLogs.isEmpty()) break;

            updateStatistics(statistics, watchLogs, contentPost, logDate);

            if (watchLogs.size() < fetchSize) break;
            cursorId = watchLogs.get(watchLogs.size() - 1).getId();
        }

        return statistics;
    }

    private List<MemberContentWatchLog> fetchWatchLogs(Long contentPostId, LocalDate logDate, Long cursorId) {
        return memberContentWatchLogRepository.findByContentPostIdAndWatchedDateWithPaging(contentPostId, logDate, cursorId, fetchSize);
    }

    private void updateStatistics(List<ContentStatistics> statistics, List<MemberContentWatchLog> watchLogs, ContentPost contentPost, LocalDate logDate) {
        long uniqueViewers = watchLogs.stream()
                .map(MemberContentWatchLog::getMemberId)
                .distinct()
                .count();

        long totalWatchTime = watchLogs.stream()
                .mapToLong(MemberContentWatchLog::getTotalPlaybackTime)
                .sum();

        for (StatisticsPeriod period : StatisticsPeriod.getAllPeriodsForDaily()) {
            LocalDate statisticsDate = getStatisticsDate(logDate, period);

            ContentStatistics stats = contentStatisticsRepository.findByContentPost_IdAndPeriodAndStatisticsDate(
                            contentPost.getId(), period, statisticsDate)
                    .orElseGet(() -> ContentStatistics.customBuilder()
                            .contentPost(contentPost)
                            .statisticsDate(statisticsDate)
                            .period(period)
                            .viewCount(0L)
                            .watchTime(0L)
                            .accumulatedViews(contentPost.getTotalViews())
                            .build());

            stats.addDailyStats(uniqueViewers, totalWatchTime);
            statistics.add(stats);
        }
    }

    private LocalDate getStatisticsDate(LocalDate logDate, StatisticsPeriod period) {
        return switch (period) {
            case DAILY -> logDate;
            case WEEKLY -> logDate.with(previousOrSame(DayOfWeek.MONDAY));
            case MONTHLY -> logDate.withDayOfMonth(1);
            case YEARLY -> logDate.withMonth(1).withDayOfMonth(1);
        };
    }

    private void validateContentPost(ContentPost contentPost) {
        if (contentPost == null) {
            throw new IllegalArgumentException("ContentPost must not be null");
        }
    }

    public List<ContentStatistics> getDailyStatistics(LocalDate date) {
        return contentStatisticsRepository.findByStatisticsDateAndPeriod(date, StatisticsPeriod.DAILY);
    }

    public List<ContentStatistics> getWeeklyStatistics(LocalDate startDate) {
        return contentStatisticsRepository.findByStatisticsDateAndPeriod(startDate, StatisticsPeriod.WEEKLY);
    }

    public List<ContentStatistics> getMonthlyStatistics(LocalDate yearMonth) {
        return contentStatisticsRepository.findByStatisticsDateAndPeriod(yearMonth, StatisticsPeriod.MONTHLY);
    }

    public List<ContentStatistics> getContentStatistics(Long contentPostId, LocalDate startDate, LocalDate endDate) {
        return contentStatisticsRepository.findByContentPostIdAndStatisticsDateBetweenAndPeriod(
                contentPostId, startDate, endDate, StatisticsPeriod.DAILY);
    }
}
