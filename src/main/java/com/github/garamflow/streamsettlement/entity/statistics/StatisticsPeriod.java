package com.github.garamflow.streamsettlement.entity.statistics;

public enum StatisticsPeriod {
    DAILY("일간"), WEEKLY("주간"), MONTHLY("월간"), YEARLY("연간");

    private final String description;

    StatisticsPeriod(String description) {
        this.description = description;
    }
}
