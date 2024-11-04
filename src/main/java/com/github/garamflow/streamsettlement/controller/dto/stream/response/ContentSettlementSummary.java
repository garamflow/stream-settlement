package com.github.garamflow.streamsettlement.controller.dto.stream.response;

import com.github.garamflow.streamsettlement.entity.settlement.Settlement;
import com.github.garamflow.streamsettlement.entity.settlement.SettlementStatus;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;

import java.time.LocalDate;

// 컨텐츠별 정산 내역을 위한 내부 record
public record ContentSettlementSummary(
        Long contentId,                // 컨텐츠 ID
        String contentTitle,           // 컨텐츠 제목
        LocalDate settlementDate,      // 정산 날짜
        long dailyViews,              // 해당 일자 조회수
        long totalViews,              // 누적 조회수
        long watchTime,               // 시청 시간
        long contentAmount,           // 컨텐츠 정산금액
        long advertisementAmount,     // 광고 정산금액
        long totalAmount,             // 총 정산금액
        SettlementStatus status       // 정산 상태
) {
    public static ContentSettlementSummary from(Settlement settlement) {
        ContentStatistics statistics = settlement.getContentStatistics();
        ContentPost contentPost = statistics.getContentPost();

        return new ContentSettlementSummary(
                contentPost.getId(),
                contentPost.getTitle(),
                settlement.getSettlementDate(),
                settlement.getDailyViews(),
                settlement.getTotalViews(),
                statistics.getWatchTime(),
                settlement.getContentAmount(),
                settlement.getAdAmount(),
                settlement.getTotalAmount(),
                settlement.getStatus()
        );
    }
}
