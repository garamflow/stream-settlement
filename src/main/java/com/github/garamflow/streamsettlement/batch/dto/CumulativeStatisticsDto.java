package com.github.garamflow.streamsettlement.batch.dto;

import com.querydsl.core.annotations.QueryProjection;

public record CumulativeStatisticsDto(
        Long contentId,
        Long totalViews,
        Long totalWatchTime,
        Long accumulatedViews
) {
    @QueryProjection
    public CumulativeStatisticsDto {
    }
}
