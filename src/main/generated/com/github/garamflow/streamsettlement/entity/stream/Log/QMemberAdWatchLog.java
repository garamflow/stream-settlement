package com.github.garamflow.streamsettlement.entity.stream.Log;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QMemberAdWatchLog is a Querydsl query type for MemberAdWatchLog
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QMemberAdWatchLog extends EntityPathBase<MemberAdWatchLog> {

    private static final long serialVersionUID = -1015837078L;

    public static final QMemberAdWatchLog memberAdWatchLog = new QMemberAdWatchLog("memberAdWatchLog");

    public final NumberPath<Long> advertisementId = createNumber("advertisementId", Long.class);

    public final NumberPath<Long> contentPostId = createNumber("contentPostId", Long.class);

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final NumberPath<Long> memberId = createNumber("memberId", Long.class);

    public final NumberPath<Long> playbackPosition = createNumber("playbackPosition", Long.class);

    public final EnumPath<StreamingStatus> streamingStatus = createEnum("streamingStatus", StreamingStatus.class);

    public final DateTimePath<java.time.LocalDateTime> updatedAt = createDateTime("updatedAt", java.time.LocalDateTime.class);

    public final DatePath<java.time.LocalDate> watchedDate = createDate("watchedDate", java.time.LocalDate.class);

    public QMemberAdWatchLog(String variable) {
        super(MemberAdWatchLog.class, forVariable(variable));
    }

    public QMemberAdWatchLog(Path<? extends MemberAdWatchLog> path) {
        super(path.getType(), path.getMetadata());
    }

    public QMemberAdWatchLog(PathMetadata metadata) {
        super(MemberAdWatchLog.class, metadata);
    }

}

