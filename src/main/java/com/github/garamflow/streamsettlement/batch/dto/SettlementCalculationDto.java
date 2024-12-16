package com.github.garamflow.streamsettlement.batch.dto;

public record SettlementCalculationDto(
        Long id,
        Long contentId,
        long totalContentRevenue,
        long totalAdRevenue,
        long previousContentRevenue,
        long previousAdRevenue
) {
    public SettlementCalculationDto(Long contentId, long totalContentRevenue, long totalAdRevenue) {
        this(null, contentId, totalContentRevenue, totalAdRevenue, 0L, 0L);
    }
}
