package com.github.garamflow.streamsettlement.batch.dto;

import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StatisticsAndSettlementDto {
    private final ContentStatistics statistics;
    private final PreviousSettlementDto previousSettlement;
}
