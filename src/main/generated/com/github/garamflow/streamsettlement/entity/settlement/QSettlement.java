package com.github.garamflow.streamsettlement.entity.settlement;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QSettlement is a Querydsl query type for Settlement
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QSettlement extends EntityPathBase<Settlement> {

    private static final long serialVersionUID = -1581782322L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QSettlement settlement = new QSettlement("settlement");

    public final NumberPath<Long> adAmount = createNumber("adAmount", Long.class);

    public final NumberPath<Long> contentAmount = createNumber("contentAmount", Long.class);

    public final com.github.garamflow.streamsettlement.entity.statistics.QContentStatistics contentStatistics;

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final NumberPath<Long> dailyViews = createNumber("dailyViews", Long.class);

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final DatePath<java.time.LocalDate> settlementDate = createDate("settlementDate", java.time.LocalDate.class);

    public final EnumPath<SettlementStatus> status = createEnum("status", SettlementStatus.class);

    public final NumberPath<Long> totalAmount = createNumber("totalAmount", Long.class);

    public final NumberPath<Long> totalViews = createNumber("totalViews", Long.class);

    public final DateTimePath<java.time.LocalDateTime> updatedAt = createDateTime("updatedAt", java.time.LocalDateTime.class);

    public QSettlement(String variable) {
        this(Settlement.class, forVariable(variable), INITS);
    }

    public QSettlement(Path<? extends Settlement> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QSettlement(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QSettlement(PathMetadata metadata, PathInits inits) {
        this(Settlement.class, metadata, inits);
    }

    public QSettlement(Class<? extends Settlement> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.contentStatistics = inits.isInitialized("contentStatistics") ? new com.github.garamflow.streamsettlement.entity.statistics.QContentStatistics(forProperty("contentStatistics"), inits.get("contentStatistics")) : null;
    }

}

