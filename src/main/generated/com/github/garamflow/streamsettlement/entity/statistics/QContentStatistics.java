package com.github.garamflow.streamsettlement.entity.statistics;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QContentStatistics is a Querydsl query type for ContentStatistics
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QContentStatistics extends EntityPathBase<ContentStatistics> {

    private static final long serialVersionUID = -218331471L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QContentStatistics contentStatistics = new QContentStatistics("contentStatistics");

    public final NumberPath<Long> accumulatedViews = createNumber("accumulatedViews", Long.class);

    public final com.github.garamflow.streamsettlement.entity.stream.content.QContentPost contentPost;

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final EnumPath<StatisticsPeriod> period = createEnum("period", StatisticsPeriod.class);

    public final DatePath<java.time.LocalDate> statisticsDate = createDate("statisticsDate", java.time.LocalDate.class);

    public final NumberPath<Long> viewCount = createNumber("viewCount", Long.class);

    public final NumberPath<Long> watchTime = createNumber("watchTime", Long.class);

    public QContentStatistics(String variable) {
        this(ContentStatistics.class, forVariable(variable), INITS);
    }

    public QContentStatistics(Path<? extends ContentStatistics> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QContentStatistics(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QContentStatistics(PathMetadata metadata, PathInits inits) {
        this(ContentStatistics.class, metadata, inits);
    }

    public QContentStatistics(Class<? extends ContentStatistics> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.contentPost = inits.isInitialized("contentPost") ? new com.github.garamflow.streamsettlement.entity.stream.content.QContentPost(forProperty("contentPost"), inits.get("contentPost")) : null;
    }

}

