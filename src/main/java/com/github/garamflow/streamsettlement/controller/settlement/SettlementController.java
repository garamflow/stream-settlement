package com.github.garamflow.streamsettlement.controller.settlement;

import com.github.garamflow.streamsettlement.controller.dto.stream.response.SettlementSummaryResponse;
import com.github.garamflow.streamsettlement.entity.settlement.Settlement;
import com.github.garamflow.streamsettlement.service.settlement.SettlementService;
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

    @GetMapping("/daily")
    public ResponseEntity<SettlementSummaryResponse> getDailySettlement(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date
    ) {

        List<Settlement> settlements = settlementService.getDailySettlementSummary(date);
        return ResponseEntity.ok(SettlementSummaryResponse.from(settlements));
    }

    @GetMapping("/weekly")
    public ResponseEntity<SettlementSummaryResponse> getWeeklySettlement(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate
    ) {
        List<Settlement> settlements = settlementService.getWeeklySettlementSummary(startDate);
        return ResponseEntity.ok(SettlementSummaryResponse.from(settlements));
    }

    @GetMapping("/monthly")
    public ResponseEntity<SettlementSummaryResponse> getMonthlySettlement(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") LocalDate yearMonth
    ) {
        List<Settlement> settlements = settlementService.getMonthlySettlement(yearMonth);
        return ResponseEntity.ok(SettlementSummaryResponse.from(settlements));
    }

    @GetMapping("/contents/{contentPostId}")
    public ResponseEntity<SettlementSummaryResponse> getContentSettlement(
            @PathVariable Long contentPostId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate
    ) {
        List<Settlement> settlements = settlementService.getContentSettlement(contentPostId, startDate, endDate);
        return ResponseEntity.ok(SettlementSummaryResponse.from(settlements));
    }
}
