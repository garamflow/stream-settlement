package com.github.garamflow.streamsettlement.batch.util;

import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

public class StatisticsUtil {

    public static List<ContentStatistics> createStatistics(ContentPost contentPost, LocalDate logDate, long watchTime) {
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

    private static LocalDate getStatisticsDate(LocalDate logDate, StatisticsPeriod period) {
        return switch (period) {
            case DAILY -> logDate;
            case WEEKLY -> logDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTHLY -> logDate.withDayOfMonth(1);
            case YEARLY -> logDate.withDayOfYear(1);
        };
    }
}