package com.github.garamflow.streamsettlement.batch.dto;

import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;

/**
 * 통계와 정산 정보를 함께 처리하기 위한 복합 DTO
 * - ContentStatistics: 일일 시청 통계 엔티티
 * - SettlementCalculationDto: 해당 콘텐츠의 정산 계산 정보
 * - Settlement 엔티티 생성을 위한 중간 데이터 전달 역할
 */
public record StatisticsAndCumulativeSettlementDto(
        ContentStatistics statistics,                // 일일 시청 통계 정보
        SettlementCalculationDto cumulativeSettlementDto // 누적 정산 계산 정보
) {
}
