package com.github.garamflow.streamsettlement.repository.settlement.rate;

import com.github.garamflow.streamsettlement.entity.settlement.SettlementRate;
import com.github.garamflow.streamsettlement.entity.settlement.SettlementType;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.github.garamflow.streamsettlement.entity.settlement.QSettlementRate.settlementRate;

@Repository
@RequiredArgsConstructor
public class SettlementRateCustomRepositoryImpl implements SettlementRateCustomRepository {
    private final JPAQueryFactory jpaQueryFactory;

    @Override
    public Optional<SettlementRate> findApplicableRate(
            SettlementType type, Long views, LocalDateTime dateTime) {
        return Optional.ofNullable(
                jpaQueryFactory
                        .selectFrom(settlementRate)
                        .where(
                                settlementRate.settlementType.eq(type),
                                settlementRate.minViews.loe(views),
                                settlementRate.maxViews.isNull().or(settlementRate.maxViews.goe(views)),
                                settlementRate.appliedAt.isNull().or(settlementRate.appliedAt.loe(dateTime)),
                                settlementRate.expiredAt.isNull().or(settlementRate.expiredAt.goe(dateTime))
                        )
                        .fetchFirst()
        );
    }
}
