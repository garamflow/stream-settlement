package com.github.garamflow.streamsettlement.repository.settlement;

import com.github.garamflow.streamsettlement.entity.settlement.Settlement;

import java.util.List;

public interface SettlementCustomRepository {
    void bulkInsertSettlement(List<Settlement> settlements);
} 