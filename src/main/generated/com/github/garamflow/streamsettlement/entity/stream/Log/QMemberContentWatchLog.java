package com.github.garamflow.streamsettlement.entity.stream.Log;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QMemberContentWatchLog is a Querydsl query type for MemberContentWatchLog
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QMemberContentWatchLog extends EntityPathBase<MemberContentWatchLog> {

    private static final long serialVersionUID = 1082723740L;

    public static final QMemberContentWatchLog memberContentWatchLog = new QMemberContentWatchLog("memberContentWatchLog");

    public final NumberPath<Long> contentPostId = createNumber("contentPostId", Long.class);

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final NumberPath<Long> lastPlaybackPosition = createNumber("lastPlaybackPosition", Long.class);

    public final NumberPath<Long> memberId = createNumber("memberId", Long.class);

    public final EnumPath<StreamingStatus> streamingStatus = createEnum("streamingStatus", StreamingStatus.class);

    public final NumberPath<Long> totalPlaybackTime = createNumber("totalPlaybackTime", Long.class);

    public final DateTimePath<java.time.LocalDateTime> updatedAt = createDateTime("updatedAt", java.time.LocalDateTime.class);

    public final DatePath<java.time.LocalDate> watchedDate = createDate("watchedDate", java.time.LocalDate.class);

    public QMemberContentWatchLog(String variable) {
        super(MemberContentWatchLog.class, forVariable(variable));
    }

    public QMemberContentWatchLog(Path<? extends MemberContentWatchLog> path) {
        super(path.getType(), path.getMetadata());
    }

    public QMemberContentWatchLog(PathMetadata metadata) {
        super(MemberContentWatchLog.class, metadata);
    }

}

