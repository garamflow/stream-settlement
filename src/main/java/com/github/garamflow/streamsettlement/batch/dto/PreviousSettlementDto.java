package com.github.garamflow.streamsettlement.batch.dto;

import com.querydsl.core.annotations.QueryProjection;

public record PreviousSettlementDto(
        Long contentPostId,
        Long previousTotalContentRevenue,
        Long previousTotalAdRevenue
) {
    @QueryProjection
    public PreviousSettlementDto {
    }
}
