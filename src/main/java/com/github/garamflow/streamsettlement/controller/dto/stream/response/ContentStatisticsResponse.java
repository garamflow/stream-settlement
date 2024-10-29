package com.github.garamflow.streamsettlement.controller.dto.stream.response;

import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;

public record ContentStatisticsResponse(
        Long ContentPostId,
        String title,
        long viewCount,
        long watchTime
) {
    public static ContentStatisticsResponse from(ContentStatistics statistics) {
        return new ContentStatisticsResponse(
                statistics.getContentPost().getId(),
                statistics.getContentPost().getTitle(),
                statistics.getViewCount(),
                statistics.getWatchTime()
        );
    }
}