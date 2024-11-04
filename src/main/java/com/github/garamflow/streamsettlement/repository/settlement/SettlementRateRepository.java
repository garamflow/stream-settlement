package com.github.garamflow.streamsettlement.repository.settlement;

import com.github.garamflow.streamsettlement.entity.settlement.SettlementRate;
import com.github.garamflow.streamsettlement.entity.settlement.SettlementType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SettlementRateRepository extends JpaRepository<SettlementRate, Long> {

    // 정산 유형별 요율 조회 (조회수 범위 오름차순)
    List<SettlementRate> findBySettlementTypeOrderByMinViewsAsc(SettlementType settlementType);

}
