package com.github.garamflow.streamsettlement.batch.dto;

/**
 * 정산 계산 데이터 전달 객체
 * - 현재 정산 금액과 이전 누적 정산 금액을 포함하여 최종 정산액 계산에 활용
 */
public record SettlementCalculationDto(
        Long id,                     // 정산 ID
        Long contentId,              // 콘텐츠 ID
        long totalContentRevenue,    // 현재까지의 총 콘텐츠 수익
        long totalAdRevenue,         // 현재까지의 총 광고 수익
        long previousContentRevenue, // 이전까지의 누적 콘텐츠 수익
        long previousAdRevenue      // 이전까지의 누적 광고 수익
) {
    /**
     * 신규 정산 데이터 생성을 위한 생성자
     * - 이전 정산 내역이 없는 경우 사용
     */
    public SettlementCalculationDto(Long contentId, long totalContentRevenue, long totalAdRevenue) {
        this(null, contentId, totalContentRevenue, totalAdRevenue, 0L, 0L);
    }
}
