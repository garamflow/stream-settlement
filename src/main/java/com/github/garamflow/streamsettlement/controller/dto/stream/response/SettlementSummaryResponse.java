package com.github.garamflow.streamsettlement.controller.dto.stream.response;

import com.github.garamflow.streamsettlement.entity.settlement.Settlement;

import java.util.List;

public record SettlementSummaryResponse(
        long totalSettlementAmount,          // 전체 정산 금액 합계
        long totalContentAmount,             // 전체 컨텐츠 정산 금액 합계
        long totalAdvertisementAmount,       // 전체 광고 정산 금액 합계
        List<ContentSettlementSummary> contentSettlements  // 컨텐츠별 정산 내역
) {
    public static SettlementSummaryResponse from(List<Settlement> settlements) {
        // 컨텐츠별 정산 내역 생성
        List<ContentSettlementSummary> contentSettlements = settlements.stream()
                .map(ContentSettlementSummary::from)
                .toList();

        // 총 금액 계산
        long totalContentAmount = settlements.stream()
                .mapToLong(Settlement::getContentAmount)
                .sum();

        long totalAdAmount = settlements.stream()
                .mapToLong(Settlement::getAdAmount)
                .sum();

        return new SettlementSummaryResponse(
                totalContentAmount + totalAdAmount,
                totalContentAmount,
                totalAdAmount,
                contentSettlements
        );
    }
}

