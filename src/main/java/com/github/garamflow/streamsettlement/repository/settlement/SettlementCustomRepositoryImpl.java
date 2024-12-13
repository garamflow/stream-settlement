package com.github.garamflow.streamsettlement.repository.settlement;


import com.github.garamflow.streamsettlement.batch.dto.PreviousSettlementDto;
import com.github.garamflow.streamsettlement.entity.settlement.QSettlement;
import com.github.garamflow.streamsettlement.entity.settlement.Settlement;
import com.github.garamflow.streamsettlement.entity.settlement.SettlementStatus;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.github.garamflow.streamsettlement.entity.settlement.QSettlement.settlement;

@Repository
@RequiredArgsConstructor
public class SettlementCustomRepositoryImpl implements SettlementCustomRepository {
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final JPAQueryFactory jpaQueryFactory;

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

        // 동일한 contentPostId와 settlementDate에 대해 중복 제거
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

    @Override
    public List<Settlement> findSettlementsByDateAndStatus(
            LocalDate settlementDate,
            SettlementStatus status) {
        return jpaQueryFactory
                .selectFrom(settlement)
                .where(
                        settlement.settlementDate.eq(settlementDate),
                        settlement.status.eq(status)
                )
                .fetch();
    }

    @Override
    public List<Settlement> findUnprocessedSettlements(LocalDate settlementDate) {
        return jpaQueryFactory
                .selectFrom(settlement)
                .where(
                        settlement.settlementDate.eq(settlementDate),
                        settlement.status.eq(SettlementStatus.CALCULATED)
                )
                .fetch();
    }

    @Override
    public List<PreviousSettlementDto> findPreviousSettlementsByContentIds(
            List<Long> contentIds,
            LocalDate settlementDate
    ) {
        QSettlement subSettlement = new QSettlement("sub");

        return jpaQueryFactory
                .select(Projections.constructor(PreviousSettlementDto.class,
                        settlement.contentPostId,
                        settlement.totalContentRevenue,
                        settlement.totalAdRevenue))
                .from(settlement)
                .where(
                        settlement.contentPostId.in(contentIds),
                        settlement.settlementDate.eq(
                                JPAExpressions
                                        .select(subSettlement.settlementDate.max())
                                        .from(subSettlement)
                                        .where(
                                                subSettlement.contentPostId.eq(settlement.contentPostId),
                                                subSettlement.settlementDate.before(settlementDate)
                                        )
                        )
                )
                .fetch();
    }

    @Override
    @Transactional
    public void bulkUpdateStatus(List<Long> settlementIds, SettlementStatus status) {
        jpaQueryFactory
                .update(settlement)
                .set(settlement.status, status)
                .where(settlement.id.in(settlementIds))
                .execute();
    }

    @Override
    public List<Settlement> findByContentIdAndDateBetween(
            Long contentId, LocalDate startDate, LocalDate endDate) {
        return jpaQueryFactory
                .selectFrom(settlement)
                .where(
                        contentPostIdEq(contentId),
                        settlementDateBetween(startDate, endDate)
                )
                .fetch();
    }

    @Override
    public Settlement findTopByContentPostIdAndSettlementDateBefore(
            Long contentPostId, LocalDate settlementDate) {
        return jpaQueryFactory
                .selectFrom(settlement)
                .where(
                        contentPostIdEq(contentPostId),
                        settlement.settlementDate.before(settlementDate)
                )
                .orderBy(settlement.settlementDate.desc())
                .fetchFirst();
    }

    @Override
    public List<Settlement> findByContentPostIdInAndSettlementDateBefore(
            List<Long> contentIds,
            LocalDate date
    ) {
        return jpaQueryFactory
                .selectFrom(settlement)
                .where(
                        settlement.contentPostId.in(contentIds),
                        settlement.settlementDate.before(date)
                )
                .fetch();
    }

    @Override
    public List<Settlement> findBySettlementDate(LocalDate settlementDate) {
        QSettlement sub = new QSettlement("sub");

        return jpaQueryFactory
                .selectFrom(settlement)
                .where(
                        settlement.settlementDate.eq(settlementDate),
                        settlement.id.in(
                                JPAExpressions
                                        .select(sub.id.min())
                                        .from(sub)
                                        .where(sub.settlementDate.eq(settlementDate))
                                        .groupBy(sub.contentPostId)
                        )
                )
                .orderBy(settlement.contentPostId.asc())
                .fetch();
    }

    // 재사용 가능한 조건문들
    private BooleanExpression contentPostIdEq(Long contentPostId) {
        return contentPostId != null ? settlement.contentPostId.eq(contentPostId) : null;
    }

    private BooleanExpression settlementDateBetween(LocalDate startDate, LocalDate endDate) {
        return settlement.settlementDate.between(startDate, endDate);
    }

    private BooleanExpression settlementDateEq(LocalDate date) {
        return date != null ? settlement.settlementDate.eq(date) : null;
    }

    private BooleanExpression statusEq(SettlementStatus status) {
        return status != null ? settlement.status.eq(status) : null;
    }
} 