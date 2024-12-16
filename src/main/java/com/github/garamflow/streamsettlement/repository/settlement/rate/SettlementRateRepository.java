package com.github.garamflow.streamsettlement.repository.settlement.rate;

import com.github.garamflow.streamsettlement.entity.settlement.SettlementRate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementRateRepository extends JpaRepository<SettlementRate, Long> {
}
