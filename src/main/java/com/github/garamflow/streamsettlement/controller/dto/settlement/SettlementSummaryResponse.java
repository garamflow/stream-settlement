package com.github.garamflow.streamsettlement.controller.dto.settlement;

import com.github.garamflow.streamsettlement.entity.settlement.Settlement;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record SettlementSummaryResponse(long totalSettlementAmount,          // 전체 정산 금액 합계
                                        long totalContentAmount,             // 전체 컨텐츠 정산 금액 합계
                                        long totalAdvertisementAmount,       // 전체 광고 정산 금액 합계
                                        List<ContentSettlementSummary> contentSettlements  // 컨텐츠별 정산 내역
) {
    public static SettlementSummaryResponse from(List<Settlement> settlements, List<ContentStatistics> statistics) {
        Map<Long, ContentStatistics> statsMap = statistics.stream().collect(Collectors.toMap(stats -> stats.getContentPost().getId(), stats -> stats));

        List<ContentSettlementSummary> contentSettlements = settlements.stream().map(settlement -> ContentSettlementSummary.from(settlement, statsMap.get(settlement.getContentPostId()))).toList();

        long totalContentAmount = settlements.stream().mapToLong(Settlement::getContentRevenue).sum();

        long totalAdAmount = settlements.stream().mapToLong(Settlement::getAdRevenue).sum();

        return new SettlementSummaryResponse(totalContentAmount + totalAdAmount, totalContentAmount, totalAdAmount, contentSettlements);
    }
}

