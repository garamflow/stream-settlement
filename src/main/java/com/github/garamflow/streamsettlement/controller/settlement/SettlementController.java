package com.github.garamflow.streamsettlement.controller.settlement;

import com.github.garamflow.streamsettlement.controller.dto.stream.response.SettlementSummaryResponse;
import com.github.garamflow.streamsettlement.entity.settlement.Settlement;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.service.settlement.SettlementService;
import com.github.garamflow.streamsettlement.service.statistics.ContentStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/contents/{contentPostId}")
    public ResponseEntity<SettlementSummaryResponse> getContentSettlement(
            @PathVariable Long contentPostId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate
    ) {
        List<Settlement> settlements = settlementService.getContentSettlement(contentPostId, startDate, endDate);
        List<ContentStatistics> statistics = contentStatisticsService.getContentStatistics(contentPostId, startDate, endDate);
        return ResponseEntity.ok(SettlementSummaryResponse.from(settlements, statistics));
    }
}
