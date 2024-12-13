package com.github.garamflow.streamsettlement.batch.processor;

import com.github.garamflow.streamsettlement.batch.dto.PreviousSettlementDto;
import com.github.garamflow.streamsettlement.batch.dto.StatisticsAndSettlementDto;
import com.github.garamflow.streamsettlement.domain.AdRevenueRange;
import com.github.garamflow.streamsettlement.domain.ContentRevenueRange;
import com.github.garamflow.streamsettlement.entity.settlement.Settlement;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class SettlementItemProcessor implements ItemProcessor<StatisticsAndSettlementDto, Settlement> {

    @Value("#{jobParameters['targetDate']}")
    private String targetDate;

    @Override
    public Settlement process(StatisticsAndSettlementDto item) throws Exception {
        ContentStatistics statistics = item.statistics();
        PreviousSettlementDto previousSettlement = item.previousSettlement();

        // 누적 수익 계산
        long totalContentRevenue = ContentRevenueRange.calculateRevenueByViews(
                statistics.getAccumulatedViews()
        );
        long totalAdRevenue = AdRevenueRange.calculateRevenueByViews(
                statistics.getWatchTime()
        );

        // 일일 정산 계산 (음수 방지)
        long dailyContentRevenue = Math.max(0,
                totalContentRevenue - previousSettlement.previousTotalContentRevenue()
        );
        long dailyAdRevenue = Math.max(0,
                totalAdRevenue - previousSettlement.previousTotalAdRevenue()
        );

        log.debug("Processing settlement - contentId: {}, dailyContentRevenue: {}, dailyAdRevenue: {}",
                statistics.getContentPost().getId(), dailyContentRevenue, dailyAdRevenue);

        return Settlement.customBuilder()
                .contentPostId(statistics.getContentPost().getId())
                .contentRevenue(dailyContentRevenue)
                .adRevenue(dailyAdRevenue)
                .totalContentRevenue(totalContentRevenue)
                .totalAdRevenue(totalAdRevenue)
                .settlementDate(LocalDate.parse(targetDate))
                .build();
    }
}