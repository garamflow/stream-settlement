package com.github.garamflow.streamsettlement.service.statistics;

import com.github.garamflow.streamsettlement.controller.dto.stream.response.ContentStatisticsResponse;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import com.github.garamflow.streamsettlement.repository.statistics.StatisticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static java.time.temporal.TemporalAdjusters.previousOrSame;

@Service
@RequiredArgsConstructor
@Transactional
public class StatisticsService {

    private final StatisticsRepository statisticsRepository;

    public List<ContentStatisticsResponse> getTop5Views(StatisticsPeriod period) {
        LocalDate targetDate = getTargetDate(period);
        Pageable pageable = PageRequest.of(0, 5);
        List<ContentStatistics> statistics = statisticsRepository.findTop5ByPeriodAndStatisticsDateOrderByViewCountDesc(period, targetDate, pageable);

        return statistics.stream()
                .map(ContentStatisticsResponse::from)
                .toList();
    }

    public List<ContentStatisticsResponse> getTop5WatchTime(StatisticsPeriod period) {
        LocalDate targetDate = getTargetDate(period);
        Pageable pageable = PageRequest.of(0, 5);
        List<ContentStatistics> statistics = statisticsRepository
                .findTop5ByPeriodAndStatisticsDateOrderByTotalWatchTimeDesc(period, targetDate, pageable);

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
}
