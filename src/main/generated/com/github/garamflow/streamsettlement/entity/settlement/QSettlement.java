package com.github.garamflow.streamsettlement.entity.settlement;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QSettlement is a Querydsl query type for Settlement
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QSettlement extends EntityPathBase<Settlement> {

    private static final long serialVersionUID = -1581782322L;

    public static final QSettlement settlement = new QSettlement("settlement");

    public final NumberPath<Long> adRevenue = createNumber("adRevenue", Long.class);

    public final NumberPath<Long> contentPostId = createNumber("contentPostId", Long.class);

    public final NumberPath<Long> contentRevenue = createNumber("contentRevenue", Long.class);

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final DatePath<java.time.LocalDate> settlementDate = createDate("settlementDate", java.time.LocalDate.class);

    public final EnumPath<SettlementStatus> status = createEnum("status", SettlementStatus.class);

    public final NumberPath<Long> totalAdRevenue = createNumber("totalAdRevenue", Long.class);

    public final NumberPath<Long> totalContentRevenue = createNumber("totalContentRevenue", Long.class);

    public QSettlement(String variable) {
        super(Settlement.class, forVariable(variable));
    }

    public QSettlement(Path<? extends Settlement> path) {
        super(path.getType(), path.getMetadata());
    }

    public QSettlement(PathMetadata metadata) {
        super(Settlement.class, metadata);
    }

}

