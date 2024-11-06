package com.github.garamflow.streamsettlement.repository.settlement;

import com.github.garamflow.streamsettlement.entity.settlement.SettlementRate;
import com.github.garamflow.streamsettlement.entity.settlement.SettlementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface SettlementRateRepository extends JpaRepository<SettlementRate, Long> {

    // 정산 유형과 조회수에 맞는 정산 요율 조회
    @Query("""
            SELECT sr FROM SettlementRate sr
            WHERE sr.settlementType = :type
            AND :views BETWEEN sr.minViews AND COALESCE(sr.maxViews, :views)
            AND (sr.appliedAt IS NULL OR sr.appliedAt <= :dateTime)
            AND (sr.expiredAt IS NULL OR :dateTime <= sr.expiredAt)
            """)
    Optional<SettlementRate> findApplicableRate(
            @Param("type") SettlementType type,
            @Param("views") Long views,
            @Param("dateTime") LocalDateTime dateTime
    );
}
