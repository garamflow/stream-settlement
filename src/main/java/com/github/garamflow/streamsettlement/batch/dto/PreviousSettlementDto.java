package com.github.garamflow.streamsettlement.batch.dto;

public record PreviousSettlementDto(
        Long contentPostId,
        Long previousTotalContentRevenue,
        Long previousTotalAdRevenue
) {
}
