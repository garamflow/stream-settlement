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

/**
 * 정산 데이터 처리기
 * - 통계 데이터를 기반으로 일일 정산액 계산
 * - 누적 정산액 관리
 */
@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class SettlementItemProcessor implements ItemProcessor<StatisticsAndCumulativeSettlementDto, Settlement> {

    @Value("#{jobParameters['targetDate']}")
    private LocalDate targetDate;

    /**
     * 정산 데이터 처리
     * - 일일 콘텐츠/광고 수익 계산
     * - 누적 수익 정보 포함하여 정산 엔티티 생성
     * 
     * @param item 통계 및 누적 정산 정보
     * @return 생성된 정산 엔티티
     */
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

    /**
     * 일일 콘텐츠 수익 계산
     * - 전체 수익에서 이전 누적 수익을 차감
     * - 음수 방지를 위한 최소값 0 보장
     */
    private long calculateDailyContentRevenue(SettlementCalculationDto calculationDto) {
        return Math.max(0,
                calculationDto.totalContentRevenue() - calculationDto.previousContentRevenue());
    }

    /**
     * 일일 광고 수익 계산
     * - 전체 수익에서 이전 누적 수익을 차감
     * - 음수 방지를 위한 최소값 0 보장
     */
    private long calculateDailyAdRevenue(SettlementCalculationDto calculationDto) {
        return Math.max(0,
                calculationDto.totalAdRevenue() - calculationDto.previousAdRevenue());
    }

    /**
     * 정산 처리 로깅
     */
    private void logSettlementProcessing(Long contentId, long dailyContentRevenue, long dailyAdRevenue) {
        log.debug("정산 처리 - contentId: {}, 일일 콘텐츠 수익: {}, 일일 광고 수익: {}",
                contentId, dailyContentRevenue, dailyAdRevenue);
    }

    /**
     * 정산 엔티티 생성
     * - 일일 수익과 누적 수익 정보를 포함
     */
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
