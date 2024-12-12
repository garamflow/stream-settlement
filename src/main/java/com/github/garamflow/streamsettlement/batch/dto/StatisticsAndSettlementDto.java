package com.github.garamflow.streamsettlement.batch.dto;

import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;

public record StatisticsAndSettlementDto(
        ContentStatistics statistics,
        PreviousSettlementDto previousSettlement
) {
}
