package com.github.garamflow.streamsettlement.entity.settlement;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QSettlementRate is a Querydsl query type for SettlementRate
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QSettlementRate extends EntityPathBase<SettlementRate> {

    private static final long serialVersionUID = -617573170L;

    public static final QSettlementRate settlementRate = new QSettlementRate("settlementRate");

    public final DateTimePath<java.time.LocalDateTime> appliedAt = createDateTime("appliedAt", java.time.LocalDateTime.class);

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final DateTimePath<java.time.LocalDateTime> expiredAt = createDateTime("expiredAt", java.time.LocalDateTime.class);

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final NumberPath<Long> maxViews = createNumber("maxViews", Long.class);

    public final NumberPath<Long> minViews = createNumber("minViews", Long.class);

    public final NumberPath<java.math.BigDecimal> rate = createNumber("rate", java.math.BigDecimal.class);

    public final EnumPath<SettlementType> settlementType = createEnum("settlementType", SettlementType.class);

    public QSettlementRate(String variable) {
        super(SettlementRate.class, forVariable(variable));
    }

    public QSettlementRate(Path<? extends SettlementRate> path) {
        super(path.getType(), path.getMetadata());
    }

    public QSettlementRate(PathMetadata metadata) {
        super(SettlementRate.class, metadata);
    }

}

