package com.github.garamflow.streamsettlement.service.statistics;

import com.github.garamflow.streamsettlement.controller.dto.stream.response.ContentStatisticsResponse;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import com.github.garamflow.streamsettlement.repository.statistics.ContentStatisticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    public List<ContentStatisticsResponse> getTop5Views(StatisticsPeriod period) {
        LocalDate targetDate = getTargetDate(period);
        Pageable pageable = PageRequest.of(0, 5);
        List<ContentStatistics> statistics = contentStatisticsRepository.findTop5ByPeriodAndStatisticsDateOrderByViewCountDesc(period, targetDate, pageable);

        return statistics.stream()
                .map(ContentStatisticsResponse::from)
                .toList();
    }

    public List<ContentStatisticsResponse> getTop5WatchTime(StatisticsPeriod period) {
        LocalDate targetDate = getTargetDate(period);
        Pageable pageable = PageRequest.of(0, 5);
        List<ContentStatistics> statistics = contentStatisticsRepository
                .findTop5ByPeriodAndStatisticsDateOrderByWatchTimeDesc(period, targetDate, pageable);

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

    public List<ContentStatistics> createDailyStatistics(ContentPost contentPost, LocalDate logDate, long watchTime) {
        validateContentPost(contentPost);
        List<ContentStatistics> statistics = new ArrayList<>();
        for (StatisticsPeriod period : StatisticsPeriod.getAllPeriodsForDaily()) {
            statistics.add(new ContentStatistics.Builder()
                    .contentPost(contentPost)
                    .statisticsDate(getStatisticsDate(logDate, period))
                    .period(period)
                    .viewCount(1L)
                    .watchTime(watchTime)
                    .accumulatedViews(contentPost.getTotalViews())
                    .build());
        }
        return statistics;
    }

    private LocalDate getStatisticsDate(LocalDate logDate, StatisticsPeriod period) {
        return switch (period) {
            case DAILY -> logDate;
            case WEEKLY -> logDate.with(previousOrSame(DayOfWeek.MONDAY));
            case MONTHLY -> logDate.withDayOfMonth(1);
            case YEARLY -> logDate.withDayOfYear(1);
        };
    }

    private void validateContentPost(ContentPost contentPost) {
        if (contentPost == null) {
            throw new IllegalArgumentException("ContentPost must not be null");
        }
    }
}
