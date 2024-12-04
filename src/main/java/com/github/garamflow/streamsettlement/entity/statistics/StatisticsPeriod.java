package com.github.garamflow.streamsettlement.entity.statistics;

import java.util.List;

public enum StatisticsPeriod {
    DAILY("일간"), WEEKLY("주간"), MONTHLY("월간"), YEARLY("연간");

    private final String description;

    StatisticsPeriod(String description) {
        this.description = description;
    }

    public static List<StatisticsPeriod> getAllPeriodsForDaily() {
        return List.of(DAILY, WEEKLY, MONTHLY, YEARLY);
    }
}
