package com.github.garamflow.streamsettlement.repository.settlement;

import com.github.garamflow.streamsettlement.entity.settlement.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    // 특정 날짜의 정산 데이터 조회
    List<Settlement> findBySettlementDate(LocalDate settlementDate);

    // 특정 기간의 정산 데이터 조회
    List<Settlement> findBySettlementDateBetween(LocalDate startDate, LocalDate endDate);

    // 컨텐츠 ID별 특정 기간의 정산 데이터 조회
    List<Settlement> findByContentStatisticsContentPostIdAndSettlementDateBetween(
            Long contentPostId, LocalDate startDate, LocalDate endDate);

}
