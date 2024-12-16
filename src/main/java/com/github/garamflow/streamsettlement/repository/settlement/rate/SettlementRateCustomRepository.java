package com.github.garamflow.streamsettlement.repository.settlement.rate;

import com.github.garamflow.streamsettlement.entity.settlement.SettlementRate;
import com.github.garamflow.streamsettlement.entity.settlement.SettlementType;

import java.time.LocalDateTime;
import java.util.Optional;

public interface SettlementRateCustomRepository {
    Optional<SettlementRate> findApplicableRate(
            SettlementType type,
            Long views,
            LocalDateTime dateTime
    );
}
