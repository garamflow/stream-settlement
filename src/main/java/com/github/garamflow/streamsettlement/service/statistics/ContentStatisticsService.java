package com.github.garamflow.streamsettlement.service.statistics;

import com.github.garamflow.streamsettlement.controller.dto.statistics.ContentStatisticsResponse;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import com.github.garamflow.streamsettlement.repository.statistics.ContentStatisticsQuerydslRepository;
import com.github.garamflow.streamsettlement.repository.statistics.ContentStatisticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static java.time.temporal.TemporalAdjusters.previousOrSame;

@Service
@RequiredArgsConstructor
@Transactional
public class ContentStatisticsService {

    private final ContentStatisticsQuerydslRepository contentStatisticsQuerydslRepository;
    private final ContentStatisticsRepository contentStatisticsRepository;

    // 기간별 Top5 조회 로직 추가 (조회수 기준)
    public List<ContentStatisticsResponse> getTop5Views(StatisticsPeriod period) {
        LocalDate targetDate = getTargetDate(period);
        List<ContentStatistics> statistics = contentStatisticsQuerydslRepository.findTop5ByViewCount(period, targetDate);
        return statistics.stream()
                .map(ContentStatisticsResponse::from)
                .toList();
    }

    // 기간별 Top5 조회 로직 추가 (시청시간 기준)
    public List<ContentStatisticsResponse> getTop5WatchTime(StatisticsPeriod period) {
        LocalDate targetDate = getTargetDate(period);
        List<ContentStatistics> statistics = contentStatisticsQuerydslRepository.findTop5ByWatchTime(period, targetDate);
        return statistics.stream()
                .map(ContentStatisticsResponse::from)
                .toList();
    }

    // From~To 기간 지정 Top5 조회 (조회수 기준)
    public List<ContentStatisticsResponse> getTop5ViewsBetween(StatisticsPeriod period, LocalDate startDate, LocalDate endDate) {
        // Repository에 findTop5ByViewCountBetweenAndPeriod(startDate,endDate,period) 추가 필요
        List<ContentStatistics> statistics = contentStatisticsQuerydslRepository.findTop5ByViewCountBetweenAndPeriod(startDate, endDate, period);
        return statistics.stream()
                .map(ContentStatisticsResponse::from)
                .toList();
    }

    // From~To 기간 지정 Top5 조회 (시청시간 기준)
    public List<ContentStatisticsResponse> getTop5WatchTimeBetween(StatisticsPeriod period, LocalDate startDate, LocalDate endDate) {
        // Repository에 findTop5ByWatchTimeBetweenAndPeriod(startDate,endDate,period) 추가 필요
        List<ContentStatistics> statistics = contentStatisticsQuerydslRepository.findTop5ByWatchTimeBetweenAndPeriod(startDate, endDate, period);
        return statistics.stream()
                .map(ContentStatisticsResponse::from)
                .toList();
    }

    // 일간 조회
    public List<ContentStatistics> getDailyStatistics(LocalDate date) {
        return contentStatisticsRepository.findByStatisticsDateAndPeriod(date, StatisticsPeriod.DAILY);
    }

    // 주간 조회
    public List<ContentStatistics> getWeeklyStatistics(LocalDate startDate) {
        return contentStatisticsRepository.findByStatisticsDateAndPeriod(startDate, StatisticsPeriod.WEEKLY);
    }

    // 월간 조회
    public List<ContentStatistics> getMonthlyStatistics(LocalDate yearMonth) {
        return contentStatisticsRepository.findByStatisticsDateAndPeriod(yearMonth, StatisticsPeriod.MONTHLY);
    }

    // 연간 조회
    public List<ContentStatistics> getYearlyStatistics(LocalDate year) {
        return contentStatisticsRepository.findByStatisticsDateAndPeriod(year, StatisticsPeriod.YEARLY);
    }

    // From~To 기간 지정 통계 조회 (예: 일간 기간 조회)
    public List<ContentStatistics> getStatisticsBetween(StatisticsPeriod period, LocalDate startDate, LocalDate endDate) {
        // Repository에 findByStatisticsDateBetweenAndPeriod(startDate, endDate, period) 추가 필요
        return contentStatisticsQuerydslRepository.findByStatisticsDateBetweenAndPeriod(startDate, endDate, period);
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
