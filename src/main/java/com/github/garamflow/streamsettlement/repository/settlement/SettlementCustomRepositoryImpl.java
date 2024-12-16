package com.github.garamflow.streamsettlement.repository.settlement;


import com.github.garamflow.streamsettlement.entity.settlement.Settlement;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class SettlementCustomRepositoryImpl implements SettlementCustomRepository {
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    @Transactional
    public void bulkInsertSettlement(List<Settlement> settlements) {
        String sql = """
                INSERT INTO settlement (content_post_id, content_revenue, ad_revenue,
                                        total_content_revenue, total_ad_revenue, settlement_date, status)
                VALUES (:contentPostId, :contentRevenue, :adRevenue,
                        :totalContentRevenue, :totalAdRevenue, :settlementDate, :status)
                ON DUPLICATE KEY UPDATE
                    content_revenue = VALUES(content_revenue),
                    ad_revenue = VALUES(ad_revenue),
                    total_content_revenue = VALUES(total_content_revenue),
                    total_ad_revenue = VALUES(total_ad_revenue),
                    status = VALUES(status)
                """;

        // 동일한 (contentPostId, settlementDate) 중복 제거
        Map<String, Settlement> uniqueSettlements = settlements.stream()
                .collect(Collectors.toMap(
                        s -> s.getContentPostId() + "_" + s.getSettlementDate(),
                        s -> s,
                        (existing, replacement) -> existing
                ));

        namedParameterJdbcTemplate.batchUpdate(
                sql,
                uniqueSettlements.values().stream()
                        .map(this::getSettlementParameterSource)
                        .toArray(MapSqlParameterSource[]::new)
        );
    }

    private MapSqlParameterSource getSettlementParameterSource(Settlement settlement) {
        return new MapSqlParameterSource()
                .addValue("contentPostId", settlement.getContentPostId())
                .addValue("contentRevenue", settlement.getContentRevenue())
                .addValue("adRevenue", settlement.getAdRevenue())
                .addValue("totalContentRevenue", settlement.getTotalContentRevenue())
                .addValue("totalAdRevenue", settlement.getTotalAdRevenue())
                .addValue("settlementDate", settlement.getSettlementDate())
                .addValue("status", settlement.getStatus().name());
    }
}