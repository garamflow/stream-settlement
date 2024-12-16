package com.github.garamflow.streamsettlement.controller.settlement;

import com.github.garamflow.streamsettlement.controller.dto.settlement.SettlementSummaryResponse;
import com.github.garamflow.streamsettlement.entity.settlement.Settlement;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.service.settlement.SettlementService;
import com.github.garamflow.streamsettlement.service.statistics.ContentStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/settlements")
public class SettlementController {

    private final SettlementService settlementService;
    private final ContentStatisticsService contentStatisticsService;

    @GetMapping("/daily")
    public ResponseEntity<SettlementSummaryResponse> getDailySettlement(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date
    ) {
        List<Settlement> settlements = settlementService.getDailySettlementSummary(date);
        List<ContentStatistics> statistics = contentStatisticsService.getDailyStatistics(date);
        return ResponseEntity.ok(SettlementSummaryResponse.from(settlements, statistics));
    }

    @GetMapping("/weekly")
    public ResponseEntity<SettlementSummaryResponse> getWeeklySettlement(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate
    ) {
        List<Settlement> settlements = settlementService.getWeeklySettlementSummary(startDate);
        List<ContentStatistics> statistics = contentStatisticsService.getWeeklyStatistics(startDate);
        return ResponseEntity.ok(SettlementSummaryResponse.from(settlements, statistics));
    }

    @GetMapping("/monthly")
    public ResponseEntity<SettlementSummaryResponse> getMonthlySettlement(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") LocalDate yearMonth
    ) {
        List<Settlement> settlements = settlementService.getMonthlySettlement(yearMonth);
        List<ContentStatistics> statistics = contentStatisticsService.getMonthlyStatistics(yearMonth);
        return ResponseEntity.ok(SettlementSummaryResponse.from(settlements, statistics));
    }

    // 임의 기간 조회를 위한 신규 엔드포인트
    @GetMapping("/range")
    public ResponseEntity<SettlementSummaryResponse> getSettlementsInRange(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate
    ) {
        List<Settlement> settlements = settlementService.getSettlementsBetween(startDate, endDate);
        // 통계 또한 해당 기간 조회 필요 시 서비스의 getStatisticsBetween(period, startDate, endDate) 메서드 활용 가능
        List<ContentStatistics> statistics = contentStatisticsService.getStatisticsBetween(
                // 일단 DAILY 가정, 필요 시 period 파라미터 추가 가능
                com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod.DAILY,
                startDate,
                endDate
        );
        return ResponseEntity.ok(SettlementSummaryResponse.from(settlements, statistics));
    }

    // 연간 조회
    @GetMapping("/yearly")
    public ResponseEntity<SettlementSummaryResponse> getYearlySettlement(
            @RequestParam @DateTimeFormat(pattern = "yyyy") LocalDate year
    ) {
        List<Settlement> settlements = settlementService.getYearlySettlement(year);
        List<ContentStatistics> statistics = contentStatisticsService.getYearlyStatistics(year);
        return ResponseEntity.ok(SettlementSummaryResponse.from(settlements, statistics));
    }
}

