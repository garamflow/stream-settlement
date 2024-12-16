package com.github.garamflow.streamsettlement.repository.settlement;

import com.github.garamflow.streamsettlement.entity.settlement.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SettlementRepository extends JpaRepository<Settlement, Long>, SettlementCustomRepository {
    List<Settlement> findBySettlementDateBetween(LocalDate startDate, LocalDate endDate);

    List<Settlement> findBySettlementDate(LocalDate date);
}