package com.github.garamflow.streamsettlement.batch.dto;

import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;

public record StatisticsAndCumulativeSettlementDto(
        ContentStatistics statistics,                        // 해당 날짜의 일일 통계
        SettlementCalculationDto cumulativeSettlementDto     // 해당 콘텐츠의 현재(또는 이전)까지의 누적 정산금액
) {
}
