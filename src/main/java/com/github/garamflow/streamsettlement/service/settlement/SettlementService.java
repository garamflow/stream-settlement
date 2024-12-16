package com.github.garamflow.streamsettlement.service.settlement;

import com.github.garamflow.streamsettlement.entity.settlement.Settlement;
import com.github.garamflow.streamsettlement.repository.settlement.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SettlementService {

    private final SettlementRepository settlementRepository;

    public List<Settlement> getDailySettlementSummary(LocalDate date) {
        return settlementRepository.findBySettlementDate(date);
    }

    public List<Settlement> getWeeklySettlementSummary(LocalDate startDate) {
        LocalDate monday = startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = startDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        return settlementRepository.findBySettlementDateBetween(monday, sunday);
    }

    public List<Settlement> getMonthlySettlement(LocalDate yearMonth) {
        LocalDate firstDayOfMonth = yearMonth.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate lastDayOfMonth = yearMonth.with(TemporalAdjusters.lastDayOfMonth());
        return settlementRepository.findBySettlementDateBetween(firstDayOfMonth, lastDayOfMonth);
    }

    // 연간 조회(필요 시)
    public List<Settlement> getYearlySettlement(LocalDate year) {
        LocalDate firstDayOfYear = year.withDayOfYear(1);
        LocalDate lastDayOfYear = year.withDayOfYear(year.lengthOfYear());
        return settlementRepository.findBySettlementDateBetween(firstDayOfYear, lastDayOfYear);
    }

    // 임의 기간(From~To) 조회 메서드
    public List<Settlement> getSettlementsBetween(LocalDate startDate, LocalDate endDate) {
        return settlementRepository.findBySettlementDateBetween(startDate, endDate);
    }
}
