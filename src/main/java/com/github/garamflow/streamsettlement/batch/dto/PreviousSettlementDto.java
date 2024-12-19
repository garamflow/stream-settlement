package com.github.garamflow.streamsettlement.batch.dto;

import com.querydsl.core.annotations.QueryProjection;

/**
 * 이전 정산 내역 조회용 DTO
 * - 콘텐츠별 누적 수익(콘텐츠 수익 + 광고 수익) 계산을 위한 이전 정산 데이터 제공
 */
public record PreviousSettlementDto(
        Long contentPostId,               // 콘텐츠 ID
        Long previousTotalContentRevenue, // 이전까지의 누적 콘텐츠 수익
        Long previousTotalAdRevenue       // 이전까지의 누적 광고 수익
) {
    @QueryProjection
    public PreviousSettlementDto {
    }
}
