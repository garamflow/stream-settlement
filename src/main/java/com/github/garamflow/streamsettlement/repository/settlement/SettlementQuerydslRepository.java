package com.github.garamflow.streamsettlement.repository.settlement;

import com.github.garamflow.streamsettlement.batch.dto.PreviousSettlementDto;
import com.github.garamflow.streamsettlement.batch.dto.SettlementCalculationDto;
import com.github.garamflow.streamsettlement.entity.settlement.QSettlement;
import com.github.garamflow.streamsettlement.entity.settlement.Settlement;
import com.github.garamflow.streamsettlement.entity.settlement.SettlementStatus;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.github.garamflow.streamsettlement.entity.settlement.QSettlement.settlement;

@Repository
@RequiredArgsConstructor
public class SettlementQuerydslRepository {

    private final JPAQueryFactory jpaQueryFactory;

    public List<Settlement> findSettlementsByDateAndStatus(LocalDate settlementDate, SettlementStatus status) {
        return jpaQueryFactory
                .selectFrom(settlement)
                .where(settlementDateEq(settlementDate), statusEq(status))
                .fetch();
    }

    public Optional<Settlement> findLatestSettlementBefore(Long contentPostId, LocalDate date) {
        Settlement result = jpaQueryFactory
                .selectFrom(settlement)
                .where(
                        contentPostIdEq(contentPostId),
                        settlement.settlementDate.before(date)
                )
                .orderBy(settlement.settlementDate.desc())
                .fetchFirst();  // limit 1

        return Optional.ofNullable(result);
    }

    public List<Settlement> findUnprocessedSettlements(LocalDate settlementDate) {
        return jpaQueryFactory
                .selectFrom(settlement)
                .where(settlementDateEq(settlementDate), settlement.status.eq(SettlementStatus.CALCULATED))
                .fetch();
    }

    public List<Settlement> findByContentIdAndDateBetween(Long contentId, LocalDate startDate, LocalDate endDate) {
        return jpaQueryFactory
                .selectFrom(settlement)
                .where(contentPostIdEq(contentId), settlementDateBetween(startDate, endDate))
                .fetch();
    }

    public Settlement findTopByContentPostIdAndSettlementDateBefore(Long contentPostId, LocalDate settlementDate) {
        return jpaQueryFactory
                .selectFrom(settlement)
                .where(contentPostIdEq(contentPostId), settlement.settlementDate.before(settlementDate))
                .orderBy(settlement.settlementDate.desc())
                .fetchFirst();
    }

    public List<Settlement> findByContentPostIdInAndSettlementDateBefore(List<Long> contentIds, LocalDate date) {
        return jpaQueryFactory
                .selectFrom(settlement)
                .where(settlement.contentPostId.in(contentIds), settlement.settlementDate.before(date))
                .fetch();
    }

    public List<PreviousSettlementDto> findPreviousSettlementsByContentIds(List<Long> contentIds, LocalDate settlementDate) {
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

    public void bulkUpdateStatus(List<Long> settlementIds, SettlementStatus status) {
        jpaQueryFactory
                .update(settlement)
                .set(settlement.status, status)
                .where(settlement.id.in(settlementIds))
                .execute();
    }

    public List<Settlement> findBySettlementDate(LocalDate settlementDate) {
        QSettlement sub = new QSettlement("sub");
        return jpaQueryFactory
                .selectFrom(settlement)
                .where(
                        settlementDateEq(settlementDate),
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

    public List<SettlementCalculationDto> findCumulativeSettlementsByContentIds(List<Long> contentIds, LocalDate date) {
        QSettlement subSettlement = new QSettlement("sub");
        return jpaQueryFactory
                .select(Projections.constructor(SettlementCalculationDto.class,
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
                                                subSettlement.settlementDate.before(date)
                                        )
                        )
                )
                .fetch();
    }

    // 재사용 조건
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
