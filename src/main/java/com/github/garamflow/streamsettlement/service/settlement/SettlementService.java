package com.github.garamflow.streamsettlement.service.settlement;

import com.github.garamflow.streamsettlement.batch.settlement.SettlementScheduler;
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
    private final SettlementScheduler settlementScheduler;

    /**
     * 일별 정산 내역을 조회합니다.
     *
     * @param date 조회할 날짜
     * @return 해당 일자의 정산 내역 리스트
     */
    public List<Settlement> getDailySettlementSummary(LocalDate date) {
        return settlementRepository.findBySettlementDate(date);
    }

    /**
     * 주별 정산 내역을 조회합니다.
     * 시작일이 속한 주의 월요일부터 일요일까지의 정산 내역을 반환합니다.
     *
     * @param startDate 조회할 주의 시작 날짜
     * @return 해당 주의 정산 내역 리스트
     */
    public List<Settlement> getWeeklySettlementSummary(LocalDate startDate) {
        LocalDate monday = startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = startDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        return settlementRepository.findBySettlementDateBetween(monday, sunday);
    }

    /**
     * 월별 정산 내역을 조회합니다.
     * 해당 월의 1일부터 말일까지의 정산 내역을 반환합니다.
     *
     * @param yearMonth 조회할 연월
     * @return 해당 월의 정산 내역 리스트
     */
    public List<Settlement> getMonthlySettlement(LocalDate yearMonth) {
        LocalDate firstDayOfMonth = yearMonth.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate lastDayOfMonth = yearMonth.with(TemporalAdjusters.lastDayOfMonth());

        return settlementRepository.findBySettlementDateBetween(firstDayOfMonth, lastDayOfMonth);
    }


    /**
     * 특정 컨텐츠의 기간별 정산 내역을 조회합니다.
     *
     * @param contentId 컨텐츠 ID
     * @param startDate 조회 시작 날짜
     * @param endDate 조회 종료 날짜
     * @return 해당 컨텐츠의 정산 내역 리스트
     */
    public List<Settlement> getContentSettlement(Long contentId, LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);

        return settlementRepository.findByContentStatisticsContentPostIdAndSettlementDateBetween(
                contentId, startDate, endDate);
    }

    /**
     * 날짜 범위의 유효성을 검증합니다.
     *
     * @param startDate 시작 날짜
     * @param endDate 종료 날짜
     * @throws IllegalArgumentException 날짜 범위가 유효하지 않은 경우
     */
    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must be before or equal to end date");
        }

        if (endDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("End date cannot be in the future");
        }
    }
}
