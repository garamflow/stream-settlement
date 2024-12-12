package com.github.garamflow.streamsettlement.repository.settlement;

import com.github.garamflow.streamsettlement.batch.dto.PreviousSettlementDto;
import com.github.garamflow.streamsettlement.entity.settlement.Settlement;
import com.github.garamflow.streamsettlement.entity.settlement.SettlementStatus;

import java.time.LocalDate;
import java.util.List;

public interface SettlementCustomRepository {
    void bulkInsertSettlement(List<Settlement> settlements);

    List<Settlement> findSettlementsByDateAndStatus(LocalDate settlementDate, SettlementStatus status);

    List<Settlement> findUnprocessedSettlements(LocalDate settlementDate);

    void bulkUpdateStatus(List<Long> settlementIds, SettlementStatus status);

    List<Settlement> findByContentIdAndDateBetween(
            Long contentId,
            LocalDate startDate,
            LocalDate endDate
    );

    Settlement findTopByContentPostIdAndSettlementDateBefore(
            Long contentPostId,
            LocalDate settlementDate
    );

    List<PreviousSettlementDto> findPreviousSettlementsByContentIds(
            List<Long> contentIds,
            LocalDate settlementDate
    );

    List<Settlement> findBySettlementDate(LocalDate settlementDate);
} 