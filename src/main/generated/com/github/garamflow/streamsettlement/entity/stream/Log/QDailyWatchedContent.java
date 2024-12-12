package com.github.garamflow.streamsettlement.entity.stream.Log;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QDailyWatchedContent is a Querydsl query type for DailyWatchedContent
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QDailyWatchedContent extends EntityPathBase<DailyWatchedContent> {

    private static final long serialVersionUID = -1720658068L;

    public static final QDailyWatchedContent dailyWatchedContent = new QDailyWatchedContent("dailyWatchedContent");

    public final NumberPath<Long> contentPostId = createNumber("contentPostId", Long.class);

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final DatePath<java.time.LocalDate> watchedDate = createDate("watchedDate", java.time.LocalDate.class);

    public QDailyWatchedContent(String variable) {
        super(DailyWatchedContent.class, forVariable(variable));
    }

    public QDailyWatchedContent(Path<? extends DailyWatchedContent> path) {
        super(path.getType(), path.getMetadata());
    }

    public QDailyWatchedContent(PathMetadata metadata) {
        super(DailyWatchedContent.class, metadata);
    }

}

