package com.github.garamflow.streamsettlement.batch.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PreviousSettlementDto {
    private final Long contentPostId;
    private final Long previousTotalContentRevenue;
    private final Long previousTotalAdRevenue;
}
