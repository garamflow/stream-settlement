package com.github.garamflow.streamsettlement.batch;

import java.time.LocalDate;

public record ContentStatisticsDto(
        Long id,
        Long contentPostId,
        LocalDate statisticsDate,
        Long viewCount,
        Long watchTime,
        Integer duration
) {
}
