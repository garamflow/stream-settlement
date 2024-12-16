package com.github.garamflow.streamsettlement.batch.processor;

import com.github.garamflow.streamsettlement.batch.dto.SettlementCalculationDto;
import com.github.garamflow.streamsettlement.batch.dto.StatisticsAndCumulativeSettlementDto;
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
public class SettlementItemProcessor implements ItemProcessor<StatisticsAndCumulativeSettlementDto, Settlement> {

    @Value("#{jobParameters['targetDate']}")
    private LocalDate targetDate;

    @Override
    public Settlement process(StatisticsAndCumulativeSettlementDto item) throws Exception {
        ContentStatistics statistics = item.statistics();
        SettlementCalculationDto calculationDto = item.cumulativeSettlementDto();

        // 일일 정산액 계산
        long dailyContentRevenue = calculateDailyContentRevenue(calculationDto);
        long dailyAdRevenue = calculateDailyAdRevenue(calculationDto);

        logSettlementProcessing(statistics.getContentPost().getId(), dailyContentRevenue, dailyAdRevenue);

        return createSettlement(
                statistics.getContentPost().getId(),
                dailyContentRevenue,
                dailyAdRevenue,
                calculationDto.totalContentRevenue(),
                calculationDto.totalAdRevenue()
        );
    }

    private long calculateDailyContentRevenue(SettlementCalculationDto calculationDto) {
        return Math.max(0,
                calculationDto.totalContentRevenue() - calculationDto.previousContentRevenue());
    }

    private long calculateDailyAdRevenue(SettlementCalculationDto calculationDto) {
        return Math.max(0,
                calculationDto.totalAdRevenue() - calculationDto.previousAdRevenue());
    }

    private void logSettlementProcessing(Long contentId, long dailyContentRevenue, long dailyAdRevenue) {
        log.debug("정산 처리 - contentId: {}, 일일 콘텐츠 수익: {}, 일일 광고 수익: {}",
                contentId, dailyContentRevenue, dailyAdRevenue);
    }

    private Settlement createSettlement(
            Long contentId,
            long dailyContentRevenue,
            long dailyAdRevenue,
            long totalContentRevenue,
            long totalAdRevenue
    ) {
        return Settlement.existingBuilder()
                .contentPostId(contentId)
                .contentRevenue(dailyContentRevenue)
                .adRevenue(dailyAdRevenue)
                .totalContentRevenue(totalContentRevenue)
                .totalAdRevenue(totalAdRevenue)
                .settlementDate(targetDate)
                .build();
    }
}
